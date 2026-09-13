package com.outpost.merchant.cli.command;

import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.gateway.BalanceReport;
import com.outpost.merchant.cli.gateway.GatewayClient;
import com.outpost.merchant.cli.gateway.GatewayException;
import com.outpost.merchant.cli.merchant.Merchant;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.merchant.Psp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import org.springframework.dao.DataAccessException;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/** Commands about who the shell acts as and the balance reports. */
public class MerchantCommands {
  private final MerchantRepository merchants;
  private final GatewayClient gateway;
  private final ShellSession session;
  private final String operatorApiKey;

  /** Creates the commands over the merchant list, the Gateway, and the session. */
  public MerchantCommands(
      MerchantRepository merchants,
      GatewayClient gateway,
      ShellSession session,
      MerchantCliProperties properties) {
    this.merchants = merchants;
    this.gateway = gateway;
    this.session = session;
    this.operatorApiKey = properties.operatorApiKey();
  }

  /** Lists the active merchants; the current one is marked and configured ones are usable. */
  @Command(
      group = "Merchant",
      name = "merchants",
      description = "List the active merchants stored in Outpost")
  public String merchants() {
    List<Merchant> active;
    try {
      active = merchants.findActiveMerchants();
    } catch (DataAccessException unreadable) {
      return Database.unreadable(unreadable);
    }
    if (active.isEmpty()) {
      return "no active merchants";
    }
    StringJoiner lines = new StringJoiner("\n");
    for (Merchant merchant : active) {
      String marker = merchant.code().equals(session.merchantCode()) ? "* " : "  ";
      String usable =
          session.hasCredentials(merchant.code()) ? "" : "  (no credentials configured)";
      lines.add(marker + merchant.code() + "  " + merchant.name() + usable);
    }
    return lines.toString();
  }

  /** Switches the shell to another merchant. */
  @Command(group = "Merchant", name = "use", description = "Act as the merchant with this code")
  public String use(@Argument(index = 0, description = "merchant code") String code) {
    if (!session.hasCredentials(code)) {
      return "no credentials configured for " + code + "; see merchants";
    }
    session.use(code);
    return "acting as " + code;
  }

  /** Lists the PSPs the current merchant may pay through, as Outpost stores them. */
  @Command(
      group = "Merchant",
      name = "psps",
      description = "List the PSPs enabled for the current merchant")
  public String psps() {
    List<Psp> psps;
    try {
      psps = merchants.findEnabledPsps(session.merchantCode());
    } catch (DataAccessException unreadable) {
      return Database.unreadable(unreadable);
    }
    if (psps.isEmpty()) {
      return "no PSP enabled for " + session.merchantCode();
    }
    StringJoiner lines = new StringJoiner("\n");
    psps.forEach(psp -> lines.add(psp.code() + "  " + psp.name()));
    return lines.toString();
  }

  /** The current merchant's balance report over a period: what was posted to its account. */
  @Command(
      group = "Merchant",
      name = "report",
      description = "Balance report of the current merchant over a period of at most 30 days")
  public String report(
      @Option(longName = "from", required = true, description = "first day, YYYY-MM-DD")
          String from,
      @Option(longName = "to", required = true, description = "last day, YYYY-MM-DD") String to) {
    return readReport(
        from,
        to,
        (fromDate, toDate) -> {
          String reportUrl = gateway.requestMerchantReport(session.credentials(), fromDate, toDate);
          return format(reportUrl, gateway.readMerchantReport(session.credentials(), reportUrl));
        });
  }

  /** The platform's balance report over a period, read with the operator key. */
  @Command(
      group = "Merchant",
      name = "report-platform",
      description =
          "Balance report of every merchant, tax authority, and the platform over a period of at"
              + " most 30 days (operator)")
  public String reportPlatform(
      @Option(longName = "from", required = true, description = "first day, YYYY-MM-DD")
          String from,
      @Option(longName = "to", required = true, description = "last day, YYYY-MM-DD") String to) {
    return readReport(
        from,
        to,
        (fromDate, toDate) -> {
          String reportUrl = gateway.requestPlatformReport(operatorApiKey, fromDate, toDate);
          return format(reportUrl, gateway.readPlatformReport(operatorApiKey, reportUrl));
        });
  }

  private static String readReport(
      String from, String to, BiFunction<LocalDate, LocalDate, String> read) {
    LocalDate fromDate;
    LocalDate toDate;
    try {
      fromDate = LocalDate.parse(from);
      toDate = LocalDate.parse(to);
    } catch (DateTimeParseException invalid) {
      return "invalid date " + invalid.getParsedString() + "; use YYYY-MM-DD";
    }
    try {
      return read.apply(fromDate, toDate);
    } catch (GatewayException refused) {
      return refused.describe();
    }
  }

  private static String format(String reportUrl, BalanceReport report) {
    StringJoiner lines = new StringJoiner("\n");
    lines.add("report " + reportUrl);
    for (BalanceReport.Account account : report.accounts()) {
      lines.add(account.accountCode());
      for (BalanceReport.BalanceAccount balanceAccount : account.balanceAccounts()) {
        if (balanceAccount.balances().isEmpty()) {
          lines.add("  " + balanceAccount.balanceAccountCode() + "  (no balances)");
          continue;
        }
        lines.add("  " + balanceAccount.balanceAccountCode());
        for (BalanceReport.Balance balance : balanceAccount.balances()) {
          lines.add("    " + balance.balance() + " " + balance.currency());
        }
      }
    }
    return lines.toString();
  }
}
