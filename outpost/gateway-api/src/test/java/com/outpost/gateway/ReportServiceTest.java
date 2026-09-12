package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.gateway.report.client.BalanceReport;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.report.service.BalanceReportException;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 42L;

  @Test
  void operatorReceivesTaxBalances() {
    StubLedger ledger = new StubLedger();
    ReportService service = new ReportService(ledger, new StubMerchants());

    BalanceReport report = service.tax(GatewayPrincipal.operator());

    assertThat(report).isSameAs(ledger.tax);
  }

  @Test
  void merchantIsForbiddenFromTaxBalances() {
    ReportService service = new ReportService(new StubLedger(), new StubMerchants());

    assertThatThrownBy(() -> service.tax(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID)))
        .isInstanceOf(BalanceReportException.class)
        .extracting(exception -> ((BalanceReportException) exception).status())
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void operatorReceivesEveryMerchantBalance() {
    StubLedger ledger = new StubLedger();
    ReportService service = new ReportService(ledger, new StubMerchants());

    BalanceReport report = service.merchant(GatewayPrincipal.operator());

    assertThat(report.accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("merchant-a", "merchant-b");
  }

  @Test
  void merchantReceivesOnlyItsOwnBalance() {
    StubLedger ledger = new StubLedger();
    ReportService service =
        new ReportService(ledger, new StubMerchants(MERCHANT_ACCOUNT_ID, "merchant-b"));

    BalanceReport report = service.merchant(GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID));

    assertThat(report.accounts())
        .extracting(BalanceReport.Account::accountCode)
        .containsExactly("merchant-b");
  }

  private static final class StubLedger implements LedgerReportClient {
    private final BalanceReport tax =
        new BalanceReport(
            List.of(new BalanceReport.Account("tax-authority", "Tax Authority", List.of())));
    private final BalanceReport merchants =
        new BalanceReport(
            List.of(
                new BalanceReport.Account("merchant-a", "Merchant A", List.of()),
                new BalanceReport.Account("merchant-b", "Merchant B", List.of())));

    @Override
    public BalanceReport taxBalances() {
      return tax;
    }

    @Override
    public BalanceReport merchantBalances() {
      return merchants;
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
