package com.outpost.gateway;

import static com.outpost.gateway.OrderServiceFakes.MERCHANT_ACCOUNT_ID;
import static com.outpost.gateway.OrderServiceFakes.PAYMENT_LINK;
import static com.outpost.gateway.OrderServiceFakes.PSP_CODE;
import static com.outpost.gateway.OrderServiceFakes.PSP_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderResult;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.order.Order;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  @Test
  void createsTheOrderCallsThePspOnceQueuesOrderCreatedAndReturnsTheStoredResponse() {
    OrderServiceFakes dependencies = new OrderServiceFakes();

    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    Order stored = dependencies.repository.onlyOrder();
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(stored.getItems()).hasSize(1);
    assertThat(dependencies.queued())
        .singleElement()
        .satisfies(
            queued -> {
              assertThat(queued.type()).isEqualTo(AccountingQueueRequestTypes.ORDER_CREATED);
              assertThat(queued.originalReference()).isEqualTo(stored.getOrderReference());
              assertThat(queued.pspReference()).isEqualTo(PSP_REFERENCE);
              assertThat(queued.merchantCode()).isEqualTo("MERCHANT");
              assertThat(queued.pspCode()).isEqualTo(PSP_CODE);
              assertThat(queued.shopperCountry()).isEqualTo(stored.getShopperCountry());
              assertThat(queued.shopperCountrySubdivision())
                  .isEqualTo(stored.getShopperCountrySubdivision().orElse(null));
              assertThat(queued.netAmount()).isEqualTo(stored.getNetAmount());
              assertThat(queued.taxAmount()).isEqualTo(stored.getTaxAmount());
              assertThat(queued.grossAmount()).isEqualTo(stored.getGrossAmount());
            });
    assertThat(dependencies.pspRequests)
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.paymentReference()).isEqualTo(stored.getOrderReference());
              assertThat(request.amount().quantity()).isEqualTo(119);
            });
    assertThat(stored.getPspReference()).contains(PSP_REFERENCE);
    assertThat(stored.getPaymentLink()).contains(PAYMENT_LINK);
    assertThat(result.orderReference()).isEqualTo(stored.getOrderReference());
    assertThat(result.netAmount()).isEqualTo(100);
    assertThat(result.taxAmount()).isEqualTo(19);
    assertThat(result.grossAmount()).isEqualTo(119);
    assertThat(result.currency()).isEqualTo("EUR");
    assertThat(result.paymentLink()).isEqualTo(PAYMENT_LINK);
    assertThat(result.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.merchantLineReference()).isEqualTo("line-1");
              assertThat(line.netAmount()).isEqualTo(100);
              assertThat(line.taxAmount()).isEqualTo(19);
              assertThat(line.grossAmount()).isEqualTo(119);
              assertThat(line.taxRate()).isEqualTo("0.19");
            });
  }

  @Test
  void pspFailureReturnsFailureAndLeavesOrderWithoutPspReference() {
    OrderServiceFakes dependencies = new OrderServiceFakes();
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult("", "", ResultCode.REJECTED));

    assertFailure(dependencies, validCommand(), 503, "PSP_RETRYABLE");

    assertThat(dependencies.queued()).isEmpty();
    assertThat(dependencies.repository.onlyOrder().getPspReference()).isEmpty();
  }

  @Test
  void repeatedRequestAfterSuccessReturnsStoredResponseWithoutCallingLedgerOrPsp() {
    OrderServiceFakes dependencies = new OrderServiceFakes();
    CreateOrderResult first = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    CreateOrderResult repeated = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(repeated).isEqualTo(first);
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.queued()).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void repeatedRequestAfterPspFailureCallsThePspAgainWithTheSameReferenceAndQueuesOnce() {
    OrderServiceFakes dependencies = new OrderServiceFakes();
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult("", "", ResultCode.REJECTED));
    assertFailure(dependencies, validCommand(), 503, "PSP_RETRYABLE");

    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    Order stored = dependencies.repository.onlyOrder();
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.pspRequests)
        .extracting(CreateOrderRequest::paymentReference)
        .containsExactly(stored.getOrderReference(), stored.getOrderReference());
    assertThat(dependencies.queued())
        .extracting(AccountingQueueRequest::originalReference)
        .containsExactly(stored.getOrderReference());
    assertThat(result.orderReference()).isEqualTo(stored.getOrderReference());
    assertThat(result.paymentLink()).isEqualTo(PAYMENT_LINK);
  }

  @Test
  void differentRequestUnderUsedIdempotencyKeyIsConflict() {
    OrderServiceFakes dependencies = new OrderServiceFakes();
    dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());
    CreateOrderCommand changed =
        new CreateOrderCommand(
            "different-reference",
            "same-key",
            validCommand().shopperDetails(),
            PSP_CODE,
            validCommand().orderDetails());

    assertFailure(dependencies, changed, 409, "IDEMPOTENCY_CONFLICT");

    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.queued()).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void rejectsDuplicateLineReferences() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(List.of(line("same", 50L), line("same", 50L)), 100L, "EUR")),
        "DUPLICATE_MERCHANT_LINE_REFERENCE");
  }

  @Test
  void rejectsMixedCurrencies() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(List.of(line("line-1", 100L, "USD")), 100L, "EUR")),
        "MIXED_CURRENCIES");
  }

  @Test
  void rejectsAnOrderTotalThatDiffersFromLineTotals() {
    assertFailure(
        withOrderDetails(
            validCommand(), new OrderDetailsCommand(List.of(line("line-1", 100L)), 101L, "EUR")),
        "TOTAL_AMOUNT_MISMATCH");
  }

  @Test
  void rejectsAmountsThatOverflowMinorUnits() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(
                List.of(line("line-1", Long.MAX_VALUE)), Long.MAX_VALUE, "EUR")),
        "AMOUNT_OVERFLOW");
  }

  @Test
  void rejectsUnknownShopperCountry() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "ZZ", null, null)),
        "INVALID_COUNTRY");
  }

  @Test
  void rejectsUnknownShopperSubdivision() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", "ZZ", null)),
        "INVALID_STATE");
  }

  @Test
  void rejectsSubdivisionOwnedByAnotherCountry() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", "US-CA", null)),
        "INVALID_STATE");
  }

  @Test
  void appliesHalfEvenRoundingToTaxTies() {
    OrderServiceFakes dependencies = new OrderServiceFakes(OrderServiceFakes.rate("0.25"));

    CreateOrderResult result =
        dependencies.service.create(
            MERCHANT_ACCOUNT_ID,
            withOrderDetails(
                validCommand(), new OrderDetailsCommand(List.of(line("line-1", 6L)), 6L, "EUR")));

    assertThat(result.taxAmount()).isEqualTo(2L);
    assertThat(result.lines())
        .singleElement()
        .extracting(CreateOrderResult.OrderLineResult::taxAmount)
        .isEqualTo(2L);
  }

  @Test
  void sumsTaxAfterRoundingEachLine() {
    OrderServiceFakes dependencies = new OrderServiceFakes(OrderServiceFakes.rate("0.5"));

    CreateOrderResult result =
        dependencies.service.create(
            MERCHANT_ACCOUNT_ID,
            withOrderDetails(
                validCommand(),
                new OrderDetailsCommand(
                    List.of(line("line-1", 1L), line("line-2", 1L)), 2L, "EUR")));

    assertThat(result.taxAmount()).isZero();
    assertThat(result.lines())
        .extracting(CreateOrderResult.OrderLineResult::taxAmount)
        .containsExactly(0L, 0L);
  }

  @Test
  void rejectsAnUnavailablePsp() {
    OrderServiceFakes unavailablePsp = new OrderServiceFakes();
    unavailablePsp.merchantPsps.pspEnabled = false;
    assertFailure(unavailablePsp, validCommand(), 422, "PSP_UNAVAILABLE");
  }

  @Test
  void rejectsMissingFeeConfiguration() {
    OrderServiceFakes missingFee = new OrderServiceFakes();
    missingFee.feeConfigurations.hasFee = false;
    assertFailure(missingFee, validCommand(), 422, "MISSING_FEE_CONFIGURATION");
  }

  @Test
  void refusesAnOrderWhoseCountryHasNoTaxAuthorityBeforeStoringItOrCallingThePsp() {
    OrderServiceFakes noTaxAuthority = new OrderServiceFakes();
    noTaxAuthority.accounts.taxAuthorityCountryIds.clear();

    assertFailure(noTaxAuthority, validCommand(), 422, "MISSING_TAX_AUTHORITY");

    assertThat(noTaxAuthority.repository.orders).isEmpty();
    assertThat(noTaxAuthority.pspRequests).isEmpty();
    assertThat(noTaxAuthority.queued()).isEmpty();
  }

  private static void assertFailure(CreateOrderCommand command, String code) {
    OrderServiceFakes dependencies = new OrderServiceFakes();
    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(error -> assertThat(((OrderCreationException) error).code()).isEqualTo(code));
  }

  private static void assertFailure(
      OrderServiceFakes dependencies, CreateOrderCommand command, int status, String code) {
    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(
            error -> {
              OrderCreationException exception = (OrderCreationException) error;
              assertThat(exception.status()).isEqualTo(status);
              assertThat(exception.code()).isEqualTo(code);
            });
  }

  private static CreateOrderCommand validCommand() {
    return new CreateOrderCommand(
        "merchant-order-1",
        "same-key",
        new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", null, "10115"),
        PSP_CODE,
        new OrderDetailsCommand(List.of(line("line-1", 100L)), 100L, "EUR"));
  }

  private static OrderLineCommand line(String reference, long amount) {
    return line(reference, amount, "EUR");
  }

  private static OrderLineCommand line(String reference, long amount, String currency) {
    return new OrderLineCommand(reference, amount, currency, "DIGITAL_GOODS");
  }

  private static CreateOrderCommand withOrderDetails(
      CreateOrderCommand command, OrderDetailsCommand details) {
    return new CreateOrderCommand(
        command.merchantReference(),
        command.idempotencyKey(),
        command.shopperDetails(),
        command.pspCode(),
        details);
  }

  private static CreateOrderCommand withShopper(
      CreateOrderCommand command, ShopperDetailsCommand shopper) {
    return new CreateOrderCommand(
        command.merchantReference(),
        command.idempotencyKey(),
        shopper,
        command.pspCode(),
        command.orderDetails());
  }
}
