package com.outpost.ledger.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import com.outpost.ledger.report.repository.RegisterBalance;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class BalanceReportServiceTest {
  private static final ReportPeriod PERIOD =
      new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

  @Test
  void showsCreditRegisterNegatedAndDebitRegisterAsPostedInMajorUnits() {
    BalanceReportService service =
        new BalanceReportService(
            new FakeBalances(
                List.of(
                    new RegisterBalance(
                        "DEMO_MERCHANT", "MERCHANT", "MERCHANT_PAYABLE", "EUR", -1234L),
                    new RegisterBalance(
                        "DEMO_MERCHANT", "MERCHANT", "PENDING_FEE", "EUR", -500L))));

    BalanceReport report = service.platform(PERIOD);

    assertThat(report.accounts().getFirst().balanceAccounts())
        .extracting(
            BalanceReport.BalanceAccount::balanceAccountCode,
            balanceAccount -> balanceAccount.balances().getFirst().balance())
        .containsExactly(tuple("MERCHANT_PAYABLE", "12.34"), tuple("PENDING_FEE", "-5.00"));
  }

  @Test
  void groupsRowsIntoAccountsThenBalanceAccountsThenCurrencies() {
    BalanceReportService service =
        new BalanceReportService(
            new FakeBalances(
                List.of(
                    new RegisterBalance(
                        "DEMO_MERCHANT", "MERCHANT", "MERCHANT_PAYABLE", "EUR", -100L),
                    new RegisterBalance(
                        "DEMO_MERCHANT", "MERCHANT", "MERCHANT_PAYABLE", "USD", -200L),
                    new RegisterBalance("DEMO_MERCHANT", "MERCHANT", "PENDING_FEE", null, 0L),
                    new RegisterBalance("OUTPOST", "PLATFORM", "FEE_REVENUE", "EUR", -300L))));

    BalanceReport report = service.platform(PERIOD);

    assertThat(report.from()).isEqualTo(PERIOD.from());
    assertThat(report.to()).isEqualTo(PERIOD.to());
    assertThat(report.accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("DEMO_MERCHANT", "OUTPOST");
    assertThat(report.accounts().getFirst().balanceAccounts())
        .extracting(BalanceReport.BalanceAccount::balanceAccountCode)
        .containsExactly("MERCHANT_PAYABLE", "PENDING_FEE");
    assertThat(report.accounts().getFirst().balanceAccounts().getFirst().balances())
        .extracting(BalanceReport.Balance::currency, BalanceReport.Balance::balance)
        .containsExactly(tuple("EUR", "1.00"), tuple("USD", "2.00"));
    assertThat(report.accounts().getFirst().balanceAccounts().get(1).balances()).isEmpty();
    assertThat(report.accounts().get(1).balanceAccounts().getFirst().balances())
        .extracting(BalanceReport.Balance::balance)
        .containsExactly("3.00");
  }

  @Test
  void refusesRegisterWhosePairIsNotPermitted() {
    BalanceReportService service =
        new BalanceReportService(
            new FakeBalances(
                List.of(new RegisterBalance("DEMO_PSP", "PSP", "PENDING_FEE", "EUR", 1L))));

    assertThatThrownBy(() -> service.platform(PERIOD)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void merchantReportReadsTheNamedMerchantOverThePeriodsPostedBounds() {
    FakeBalances balances = new FakeBalances(List.of());
    BalanceReportService service = new BalanceReportService(balances);

    BalanceReport report = service.merchant("DEMO_MERCHANT", PERIOD);

    assertThat(report.accounts()).isEmpty();
    assertThat(balances.merchantCode).isEqualTo("DEMO_MERCHANT");
    assertThat(balances.postedFrom).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(balances.postedBefore).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
  }

  private static final class FakeBalances implements BalanceReportRepository {
    private final List<RegisterBalance> rows;
    private @Nullable String merchantCode;
    private @Nullable Instant postedFrom;
    private @Nullable Instant postedBefore;

    private FakeBalances(List<RegisterBalance> rows) {
      this.rows = rows;
    }

    @Override
    public List<RegisterBalance> findPlatformRegisterBalances(
        Instant postedFrom, Instant postedBefore) {
      this.postedFrom = postedFrom;
      this.postedBefore = postedBefore;
      return rows;
    }

    @Override
    public List<RegisterBalance> findMerchantRegisterBalances(
        String merchantCode, Instant postedFrom, Instant postedBefore) {
      this.merchantCode = merchantCode;
      this.postedFrom = postedFrom;
      this.postedBefore = postedBefore;
      return rows;
    }
  }
}
