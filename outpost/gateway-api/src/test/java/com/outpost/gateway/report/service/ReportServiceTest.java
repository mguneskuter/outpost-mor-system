package com.outpost.gateway.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.security.GatewayPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ReportServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 42L;
  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 30);
  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);

  private final StubLedger ledger = new StubLedger();
  private final GeneratedReports reports = new GeneratedReports(10);

  @Test
  void operatorReceivesThePlatformReportOverThePeriod() {
    ReportService service = new ReportService(ledger, new StubAccounts(null), reports);

    UUID reportId = service.createReport(GatewayPrincipal.operator(), FROM, TO);

    assertThat(ledger.requests).containsExactly("platform " + new ReportPeriod(FROM, TO));
    assertThat(service.findReport(reportId).accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("OUTPOST");
  }

  @Test
  void merchantReceivesTheReportOfItsAuthenticatedAccount() {
    ReportService service =
        new ReportService(ledger, new StubAccounts(merchant("merchant-b", true)), reports);

    UUID reportId = service.createReport(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID), FROM, TO);

    assertThat(ledger.requests).containsExactly("merchant-b " + new ReportPeriod(FROM, TO));
    assertThat(service.findReport(reportId).accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("merchant-b");
  }

  @Test
  void refusesAnInvalidPeriodBeforeTheLedgerIsRead() {
    ReportService service = new ReportService(ledger, new StubAccounts(null), reports);

    assertThatThrownBy(
            () -> service.createReport(GatewayPrincipal.operator(), FROM, FROM.plusDays(30)))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused ->
                assertThat(refused.code())
                    .isEqualTo(BalanceReportErrorCodes.INVALID_REPORT_PERIOD));
    assertThat(ledger.requests).isEmpty();
  }

  @Test
  void merchantWithoutAnActiveAccountIsRefusedBeforeTheLedgerIsRead() {
    ReportService service =
        new ReportService(ledger, new StubAccounts(merchant("merchant-b", false)), reports);

    assertThatThrownBy(
            () -> service.createReport(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID), FROM, TO))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused ->
                assertThat(refused.code()).isEqualTo(BalanceReportErrorCodes.MERCHANT_NOT_FOUND));
    assertThat(ledger.requests).isEmpty();
  }

  @Test
  void answersAnUnknownReportAsNotFound() {
    ReportService service = new ReportService(ledger, new StubAccounts(null), reports);

    assertThatThrownBy(() -> service.findReport(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            BalanceReportException.class,
            refused ->
                assertThat(refused.code()).isEqualTo(BalanceReportErrorCodes.REPORT_NOT_FOUND));
  }

  private static Account merchant(String code, boolean active) {
    return Account.of(
        MERCHANT_ACCOUNT_ID, AccountTypes.MERCHANT.getValue(), code, code, active, CREATED, ROOT);
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

  /** Holds at most one account, found only by its own id. */
  private static final class StubAccounts implements AccountRepository {
    private final Optional<Account> account;

    private StubAccounts(@Nullable Account account) {
      this.account = Optional.ofNullable(account);
    }

    @Override
    public Optional<Account> findAccountById(long accountId) {
      return account.filter(stored -> stored.getAccountId() == accountId);
    }

    @Override
    public Optional<Account> findAccountByCode(String code) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findTaxAuthorityAccountByCountryId(long countryId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findAccountByAccountType(AccountType accountType) {
      throw new UnsupportedOperationException();
    }
  }
}
