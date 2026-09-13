package com.outpost.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BalanceReportJsonTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void writesTheReportUnderItsPublishedFieldNamesAndReadsItBack() {
    BalanceReport report =
        new BalanceReport(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
            List.of(
                new BalanceReport.Account(
                    "DEMO_MERCHANT",
                    List.of(
                        new BalanceReport.BalanceAccount(
                            "MERCHANT_PAYABLE", List.of(new BalanceReport.Balance("EUR", "76.00"))),
                        new BalanceReport.BalanceAccount("PENDING_FEE", List.of())))));

    String json = mapper.writeValueAsString(report);

    assertThat(json)
        .isEqualTo(
            "{\"from\":\"2026-09-01\",\"to\":\"2026-09-30\",\"accounts\":["
                + "{\"account_code\":\"DEMO_MERCHANT\",\"balance_accounts\":["
                + "{\"balance_account_code\":\"MERCHANT_PAYABLE\","
                + "\"balances\":[{\"currency\":\"EUR\",\"balance\":\"76.00\"}]},"
                + "{\"balance_account_code\":\"PENDING_FEE\",\"balances\":[]}]}]}");
    assertThat(mapper.readValue(json, BalanceReport.class)).isEqualTo(report);
  }
}
