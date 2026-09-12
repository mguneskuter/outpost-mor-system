package com.outpost.worker.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.OrderItem;
import com.outpost.worker.accounting.RefundLineComputation.LineRefund;
import com.outpost.worker.accounting.RefundLineComputation.RefundLineRejectedException;
import com.outpost.worker.accounting.repository.LedgerTransactionRepository.RefundedTotal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RefundLineComputationTest {
  private static final Currencies.Currency EUR = Currencies.EUR.getValue();

  @Test
  void noAmountRefundsTheWholeUnrefundedLine() {
    OrderItem item = item(1000, 190, "0.19");

    LineRefund refund = RefundLineComputation.compute(item, new RefundedTotal(0, 0), null);

    assertThat(refund.net()).isEqualTo(1000);
    assertThat(refund.tax()).isEqualTo(190);
  }

  @Test
  void partialAmountComputesTaxAtTheLinesOwnRate() {
    OrderItem item = item(1000, 190, "0.19");

    LineRefund refund = RefundLineComputation.compute(item, new RefundedTotal(0, 0), 400L);

    assertThat(refund.net()).isEqualTo(400);
    assertThat(refund.tax()).isEqualTo(76L);
  }

  @Test
  void finalRefundTakesTheResidualTaxInsteadOfRoundedTax() {
    OrderItem item = item(999, 190, "0.19");

    LineRefund first = RefundLineComputation.compute(item, new RefundedTotal(0, 0), 500L);
    LineRefund last =
        RefundLineComputation.compute(item, new RefundedTotal(first.net(), first.tax()), null);

    assertThat(last.net()).isEqualTo(499);
    assertThat(last.tax()).isEqualTo(190 - first.tax());
  }

  @Test
  void rejectsAnAmountBeyondTheRemainingNet() {
    OrderItem item = item(1000, 190, "0.19");

    assertThatThrownBy(() -> RefundLineComputation.compute(item, new RefundedTotal(600, 114), 500L))
        .isInstanceOf(RefundLineRejectedException.class);
  }

  @Test
  void rejectsFullyRefundedLineAsNoOp() {
    OrderItem item = item(1000, 190, "0.19");

    assertThatThrownBy(
            () -> RefundLineComputation.compute(item, new RefundedTotal(1000, 190), null))
        .isInstanceOf(RefundLineRejectedException.class);
  }

  private static OrderItem item(long netAmount, long taxAmount, String taxRate) {
    return new OrderItem(
        1,
        1,
        ProductTypes.PHYSICAL_GOODS.getValue(),
        "line-reference",
        "merchant-line-reference",
        new Amount(EUR, netAmount),
        new Amount(EUR, taxAmount),
        new BigDecimal(taxRate));
  }
}
