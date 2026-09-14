package com.outpost.backoffice.web;

import com.outpost.backoffice.configuration.BackOfficeProperties;
import com.outpost.backoffice.configuration.BackOfficeProperties.MerchantCredentials;
import com.outpost.backoffice.gateway.BalanceReport;
import com.outpost.backoffice.gateway.GatewayClient;
import com.outpost.backoffice.gateway.GatewayException;
import com.outpost.backoffice.merchant.MerchantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The reports tab: the Gateway's period balance report for one merchant or for the platform. */
@Controller
public class ReportsController {
  /** The scope value that asks for the platform report with the operator's key. */
  static final String PLATFORM = "PLATFORM";

  private final BackOfficeProperties properties;
  private final MerchantRepository merchants;
  private final GatewayClient gateway;

  /** Creates the controller over the stored merchants and the Gateway. */
  public ReportsController(
      BackOfficeProperties properties, MerchantRepository merchants, GatewayClient gateway) {
    this.properties = properties;
    this.merchants = merchants;
    this.gateway = gateway;
  }

  /** Shows the period form and, once a period is given, the report the Gateway built. */
  @GetMapping("/reports")
  public String report(
      @RequestParam(defaultValue = PLATFORM) String scope,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to,
      Model model) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate fromDate = from == null ? today : from;
    LocalDate toDate = to == null ? today : to;
    model.addAttribute("tab", "reports");
    model.addAttribute("merchants", merchants.findActiveMerchants());
    model.addAttribute("scope", scope);
    model.addAttribute("from", fromDate);
    model.addAttribute("to", toDate);
    if (from != null || to != null) {
      try {
        List<ReportRow> rows = rows(read(scope, fromDate, toDate));
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals(rows));
      } catch (GatewayException refused) {
        model.addAttribute("error", "report refused: " + refused.describe());
      }
    }
    return "reports";
  }

  private BalanceReport read(String scope, LocalDate from, LocalDate to) {
    if (scope.equals(PLATFORM)) {
      String operatorKey = properties.operatorApiKey();
      return gateway.readPlatformReport(
          operatorKey, gateway.requestPlatformReport(operatorKey, from, to));
    }
    MerchantCredentials credentials = properties.merchants().get(scope);
    if (credentials == null) {
      throw new GatewayException(0, "NO_CREDENTIALS_FOR_" + scope);
    }
    return gateway.readMerchantReport(
        credentials, gateway.requestMerchantReport(credentials, from, to));
  }

  private static List<ReportRow> rows(BalanceReport report) {
    List<ReportRow> rows = new ArrayList<>();
    for (BalanceReport.Account account : report.accounts()) {
      for (BalanceReport.BalanceAccount balanceAccount : account.balanceAccounts()) {
        if (balanceAccount.balances().isEmpty()) {
          rows.add(
              new ReportRow(
                  account.accountCode(), balanceAccount.balanceAccountCode(), "", "0.00"));
        }
        for (BalanceReport.Balance balance : balanceAccount.balances()) {
          rows.add(
              new ReportRow(
                  account.accountCode(),
                  balanceAccount.balanceAccountCode(),
                  balance.currency(),
                  balance.balance()));
        }
      }
    }
    return rows;
  }

  /** Sums the balances per currency; a balance account without a balance adds nothing. */
  private static List<ReportRow> totals(List<ReportRow> rows) {
    Map<String, BigDecimal> sums = new LinkedHashMap<>();
    for (ReportRow row : rows) {
      if (!row.currency().isEmpty()) {
        sums.merge(row.currency(), new BigDecimal(row.balance()), BigDecimal::add);
      }
    }
    return sums.entrySet().stream()
        .map(entry -> new ReportRow("Total", "", entry.getKey(), entry.getValue().toPlainString()))
        .toList();
  }

  /** One balance of the report as the table shows it. */
  public record ReportRow(
      String accountCode, String balanceAccountCode, String currency, String balance) {}
}
