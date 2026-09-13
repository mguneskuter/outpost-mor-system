package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.report.service.BalanceReportException;
import com.outpost.gateway.report.service.GeneratedReports;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 42L;
  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 30);

  private final StubLedger ledger = new StubLedger();
  private final GeneratedReports reports = new GeneratedReports(10);

  @Test
  void operatorReceivesThePlatformReportOverThePeriod() {
    ReportService service = new ReportService(ledger, new StubMerchants(), reports);

    UUID reportId = service.createReport(GatewayPrincipal.operator(), FROM, TO);

    assertThat(ledger.requests).containsExactly("platform " + new ReportPeriod(FROM, TO));
    assertThat(service.findReport(reportId).accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("OUTPOST");
  }

  @Test
  void merchantReceivesTheReportOfItsAuthenticatedAccount() {
    ReportService service =
        new ReportService(ledger, new StubMerchants(MERCHANT_ACCOUNT_ID, "merchant-b"), reports);

    UUID reportId = service.createReport(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID), FROM, TO);

    assertThat(ledger.requests).containsExactly("merchant-b " + new ReportPeriod(FROM, TO));
    assertThat(service.findReport(reportId).accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("merchant-b");
  }

  @Test
  void refusesAnInvalidPeriodBeforeTheLedgerIsRead() {
    ReportService service = new ReportService(ledger, new StubMerchants(), reports);

    assertThatThrownBy(
            () -> service.createReport(GatewayPrincipal.operator(), FROM, FROM.plusDays(30)))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused -> {
              assertThat(refused.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
              assertThat(refused.code()).isEqualTo("INVALID_REPORT_PERIOD");
            });
    assertThat(ledger.requests).isEmpty();
  }

  @Test
  void merchantWithoutAnActiveAccountIsRefusedBeforeTheLedgerIsRead() {
    ReportService service = new ReportService(ledger, new StubMerchants(), reports);

    assertThatThrownBy(
            () -> service.createReport(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID), FROM, TO))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused -> {
              assertThat(refused.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
              assertThat(refused.code()).isEqualTo("MERCHANT_NOT_FOUND");
            });
    assertThat(ledger.requests).isEmpty();
  }

  @Test
  void answersAnUnknownReportAsNotFound() {
    ReportService service = new ReportService(ledger, new StubMerchants(), reports);

    assertThatThrownBy(() -> service.findReport(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused -> {
              assertThat(refused.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
              assertThat(refused.code()).isEqualTo("REPORT_NOT_FOUND");
            });
  }

  private static final class StubLedger implements LedgerReportClient {
    private final List<String> requests = new ArrayList<>();

    @Override
    public BalanceReport platformReport(ReportPeriod period) {
      requests.add("platform " + period);
      return report(period, "OUTPOST");
    }

    @Override
    public BalanceReport merchantReport(String merchantCode, ReportPeriod period) {
      requests.add(merchantCode + " " + period);
      return report(period, merchantCode);
    }

    private static BalanceReport report(ReportPeriod period, String accountCode) {
      return new BalanceReport(
          period.from(), period.to(), List.of(new BalanceReport.Account(accountCode, List.of())));
    }
  }

  private static final class StubMerchants implements ReportRepository {
    private final long accountId;
    private final String code;

    private StubMerchants() {
      this(0L, "");
    }

    private StubMerchants(long accountId, String code) {
      this.accountId = accountId;
      this.code = code;
    }

    @Override
    public Optional<String> findMerchantCode(long accountId) {
      return accountId == this.accountId ? Optional.of(code) : Optional.empty();
    }
  }
}
