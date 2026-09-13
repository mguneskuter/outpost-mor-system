package com.outpost.merchant.cli.command;

import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.gateway.BalanceReport;
import com.outpost.merchant.cli.gateway.GatewayClient;
import com.outpost.merchant.cli.gateway.GatewayException;
import com.outpost.merchant.cli.merchant.Merchant;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.merchant.Psp;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.dao.DataAccessException;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;

/** Commands about who the shell acts as and what Outpost owes. */
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

  /** What Outpost owes the current merchant. */
  @Command(
      group = "Merchant",
      name = "balance-merchant",
      description = "What Outpost owes the current merchant")
  public String balanceMerchant() {
    try {
      return format(gateway.merchantBalances(session.credentials()));
    } catch (GatewayException refused) {
      return refused.describe();
    }
  }

  /** What Outpost owes each tax authority, read with the operator key. */
  @Command(
      group = "Merchant",
      name = "balance-tax",
      description = "What Outpost owes each tax authority (operator)")
  public String balanceTax() {
    try {
      return format(gateway.taxBalances(operatorApiKey));
    } catch (GatewayException refused) {
      return refused.describe();
    }
  }

  private static String format(BalanceReport report) {
    if (report.accounts().isEmpty()) {
      return "nothing owed";
    }
    StringJoiner lines = new StringJoiner("\n");
    for (BalanceReport.Account account : report.accounts()) {
      lines.add(account.accountCode() + "  " + account.name());
      for (BalanceReport.Balance balance : account.balances()) {
        lines.add("  " + Money.format(balance.amount(), balance.currency()));
      }
    }
    return lines.toString();
  }
}
