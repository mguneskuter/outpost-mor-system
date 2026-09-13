package com.outpost.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.LedgerErrorResponse;
import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.ledger.accountingrequest.api.AccountingRequestController;
import com.outpost.ledger.accountingrequest.service.AccountingRequestService;
import com.outpost.ledger.accountingrequest.service.LockedAccountingQueueRequest;
import com.outpost.ledger.report.api.BalanceReportController;
import com.outpost.ledger.report.repository.BalanceLine;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import com.outpost.ledger.report.service.BalanceReportService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Every Ledger controller failure answers its documented body: a refused accounting request its
 * result, every other failure the Ledger error body. The services are real; the boundary they call
 * fails as each case chooses.
 */
class LedgerErrorContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CAPTURE_REQUEST =
      """
      {"type":"CAPTURE","original_reference":"order-1","merchant_reference":"merchant-order-1",
      "psp_code":"DEMO_PSP","psp_reference":"41","success":true,"refund_reference":null,
      "merchant_code":null,"shopper_country":null,"shopper_country_subdivision":null,
      "net_amount":null,"tax_amount":null,"gross_amount":null}
      """;

  private final FailingTransactionLocks transactionLocks = new FailingTransactionLocks();
  private final FailingBalances balances = new FailingBalances();
  private final TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue =
      new TimeOrderedQueue<>(Clock.systemUTC(), 1);
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new AccountingRequestController(
                  new AccountingRequestService(
                      transactionLocks, accountingQueue, Duration.ofMinutes(5))),
              new BalanceReportController(new BalanceReportService(balances)))
          .setControllerAdvice(new LedgerErrorAdvice())
          .build();

  @ParameterizedTest(name = "{0}")
  @MethodSource("controlledFailures")
  void answersControlledFailureWithItsStatusAndBody(
      String scenario, MockHttpServletRequestBuilder request, int status, String body)
      throws Exception {
    mockMvc.perform(request).andExpect(status().is(status)).andExpect(content().json(body));
  }

  static Stream<Arguments> controlledFailures() {
    return Stream.of(
        Arguments.of(
            "an incomplete accounting request",
            accountingRequest(CAPTURE_REQUEST.replace("\"success\":true", "\"success\":null")),
            400,
            """
            {"success":false,"result_code":"INVALID_REQUEST","type":"CAPTURE",
            "original_reference":"order-1"}
            """),
        Arguments.of(
            "an unreadable accounting request",
            accountingRequest("{"),
            400,
            "{\"code\":\"INVALID_REQUEST\"}"),
        Arguments.of(
            "a locked payment",
            accountingRequest(CAPTURE_REQUEST.replace("order-1", FailingTransactionLocks.LOCKED)),
            409,
            """
            {"success":false,"result_code":"TRANSACTION_LOCKED","type":"CAPTURE",
            "original_reference":"locked-order"}
            """));
  }

  @Test
  void answersFullQueueWithServiceUnavailableAndReleasesTheLockItTook() throws Exception {
    AccountingQueueRequest waiting = JSON.readValue(CAPTURE_REQUEST, AccountingQueueRequest.class);
    accountingQueue.add(
        new LockedAccountingQueueRequest(waiting, FailingTransactionLocks.lock("order-1")));

    mockMvc
        .perform(
            accountingRequest(CAPTURE_REQUEST.replace("order-1", FailingTransactionLocks.FREE)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            content()
                .json(
                    """
                    {"success":false,"result_code":"QUEUE_FULL","original_reference":"free-order"}
                    """));

    assertThat(transactionLocks.released)
        .extracting(TransactionLock::originalReference)
        .containsExactly(FailingTransactionLocks.FREE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyRoute")
  void answersAnUnexpectedFailureWithOneErrorLogLineTheResponseIdentifies(
      String route, MockHttpServletRequestBuilder request) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(LedgerErrorAdvice.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      MvcResult result =
          mockMvc.perform(request).andExpect(status().isInternalServerError()).andReturn();

      LedgerErrorResponse body =
          JSON.readValue(result.getResponse().getContentAsString(), LedgerErrorResponse.class);
      assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.correlationId()).isNotBlank();
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getMDCPropertyMap()).containsEntry("correlation_id", body.correlationId());
      assertThat(event.getThrowableProxy()).isNotNull();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  static Stream<Arguments> everyRoute() {
    return Stream.of(
        Arguments.of("POST /v1/accounting-request", accountingRequest(CAPTURE_REQUEST)),
        Arguments.of("GET /v1/report/balance/tax", get("/v1/report/balance/tax")),
        Arguments.of("GET /v1/report/balance/merchant", get("/v1/report/balance/merchant")),
        Arguments.of(
            "GET /v1/report/balance/merchant/{merchantCode}",
            get("/v1/report/balance/merchant/DEMO")));
  }

  private static MockHttpServletRequestBuilder accountingRequest(String body) {
    return post("/v1/accounting-request").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /**
   * Reports one reference as locked, takes the lock of one free reference, and fails unexpectedly
   * for every other.
   */
  private static final class FailingTransactionLocks implements TransactionLockRepository {
    private static final String LOCKED = "locked-order";
    private static final String FREE = "free-order";
    private final List<TransactionLock> released = new ArrayList<>();

    static TransactionLock lock(String originalReference) {
      Instant lockedAt = Instant.parse("2026-09-13T10:00:00Z");
      return new TransactionLock(originalReference, lockedAt, lockedAt.plus(Duration.ofMinutes(5)));
    }

    @Override
    public Optional<TransactionLock> insertTransactionLock(
        String originalReference, Duration lease) {
      if (LOCKED.equals(originalReference)) {
        return Optional.empty();
      }
      if (FREE.equals(originalReference)) {
        return Optional.of(lock(originalReference));
      }
      throw new IllegalStateException("Lock store unreachable");
    }

    @Override
    public void deleteTransactionLock(TransactionLock lock) {
      released.add(lock);
    }
  }

  private static final class FailingBalances implements BalanceReportRepository {
    @Override
    public List<BalanceLine> findTaxBalances() {
      throw new IllegalStateException("database unreachable");
    }

    @Override
    public List<BalanceLine> findMerchantBalances() {
      throw new IllegalStateException("database unreachable");
    }

    @Override
    public List<BalanceLine> findMerchantBalancesByMerchantCode(String merchantCode) {
      throw new IllegalStateException("database unreachable");
    }
  }
}
