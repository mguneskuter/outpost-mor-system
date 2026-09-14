package com.outpost.pspsimulator.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.outpost.pspsimulator.SimulatorTestDatabase;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.webhook.WebhookScheduler;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** The refund is acknowledged; whether it succeeded depends on the order and what is left. */
class RefundServiceDatabaseTest {
  private static final PostgreSQLContainer<?> DATABASE = SimulatorTestDatabase.start();
  private static final String PSP = "DEMO_PSP";
  private static @Nullable OrderRepository orders;
  private static @Nullable RefundRepository refunds;

  private WebhookScheduler scheduler;
  private RefundService service;

  @BeforeAll
  static void migrate() {
    DATABASE.start();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            DATABASE.getJdbcUrl() + "&currentSchema=psp_simulator",
            DATABASE.getUsername(),
            DATABASE.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("psp_simulator")
        .defaultSchema("psp_simulator")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    orders = new OrderRepository(jdbcTemplate);
    refunds = new RefundRepository(jdbcTemplate);
  }

  @AfterAll
  static void stopDatabase() {
    DATABASE.stop();
  }

  @BeforeEach
  void createService() {
    scheduler = mock(WebhookScheduler.class);
    service =
        new RefundService(
            Objects.requireNonNull(orders), Objects.requireNonNull(refunds), scheduler);
  }

  @Test
  void acknowledgesRefundWithinTheCapturedAmountAsSucceeded() {
    Order order = capturedOrder("payment-within", 1250);

    RefundService.RefundResult result = service.refund(PSP, refund(order, "refund-within", 1000));

    assertThat(result.accepted()).isTrue();
    assertThat(result.pspRefundReference()).startsWith("psp-refund-");
    ArgumentCaptor<Refund> scheduled = ArgumentCaptor.forClass(Refund.class);
    verify(scheduler).scheduleRefund(eq(order), scheduled.capture(), any());
    assertThat(scheduled.getValue().succeeded()).isTrue();
    assertThat(scheduled.getValue().amountMinor()).isEqualTo(1000);
  }

  @Test
  void acknowledgesRefundBeyondWhatEarlierRefundsLeftAsFailed() {
    Order order = capturedOrder("payment-beyond", 1250);
    service.refund(PSP, refund(order, "refund-first", 1000));

    service.refund(PSP, refund(order, "refund-second", 251));

    ArgumentCaptor<Refund> scheduled = ArgumentCaptor.forClass(Refund.class);
    verify(scheduler, times(2)).scheduleRefund(eq(order), scheduled.capture(), any());
    assertThat(scheduled.getAllValues()).extracting(Refund::succeeded).containsExactly(true, false);
  }

  @Test
  void acknowledgesRefundOfUncapturedOrderAsFailed() {
    Order order = order("payment-uncaptured", 1250, OrderStatuses.AUTHORISED);

    RefundService.RefundResult result =
        service.refund(PSP, refund(order, "refund-uncaptured", 1000));

    assertThat(result.accepted()).isTrue();
    ArgumentCaptor<Refund> scheduled = ArgumentCaptor.forClass(Refund.class);
    verify(scheduler).scheduleRefund(eq(order), scheduled.capture(), any());
    assertThat(scheduled.getValue().succeeded()).isFalse();
  }

  @Test
  void refusesPaymentReferenceOrCurrencyThatIsNotTheOrders() {
    Order order = capturedOrder("payment-mismatch", 1250);

    assertThatThrownBy(
            () ->
                service.refund(
                    PSP,
                    new RefundCommand(
                        order.pspReference(),
                        "payment-other",
                        "refund-mismatch-1",
                        1000,
                        "EUR",
                        lines())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.refund(
                    PSP,
                    new RefundCommand(
                        order.pspReference(),
                        "payment-mismatch",
                        "refund-mismatch-2",
                        1000,
                        "USD",
                        lines())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RefundCommand refund(Order order, String refundReference, long amount) {
    return new RefundCommand(
        order.pspReference(),
        order.paymentReference(),
        refundReference,
        amount,
        order.currencyCode(),
        List.of(new RefundLine("line-1", "0.25", amount * 4 / 5, amount)));
  }

  private static List<RefundLine> lines() {
    return List.of(new RefundLine("line-1", "0.25", 800, 1000));
  }

  private static Order capturedOrder(String paymentReference, long amount) {
    return order(paymentReference, amount, OrderStatuses.CAPTURED);
  }

  private static Order order(String paymentReference, long amount, OrderStatuses status) {
    OrderRepository repository = Objects.requireNonNull(orders);
    Order created = repository.insert(PSP, paymentReference, amount, "EUR").orElseThrow();
    if (status != OrderStatuses.CREATED) {
      repository.transition(PSP, created.pspReference(), OrderStatuses.CREATED, status);
    }
    return new Order(
        created.pspCode(),
        created.pspReference(),
        created.paymentReference(),
        created.amountMinor(),
        created.currencyCode(),
        status);
  }
}
