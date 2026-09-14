package com.outpost.backoffice.web;

import com.outpost.backoffice.register.RegisterBalance;
import com.outpost.backoffice.register.RegisterBalanceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The balance accounts tab: every register in scope with its debits, credits, and balance per
 * operating currency, a summary per register type and currency on top, and a total per currency.
 */
@Controller
public class BalancesController {
  private final RegisterBalanceRepository balances;

  /** Creates the controller over the stored register balances. */
  public BalancesController(RegisterBalanceRepository balances) {
    this.balances = balances;
  }

  /** Shows the registers matching the filters; a filter with nothing chosen keeps every value. */
  @GetMapping("/balances")
  public String balances(
      @RequestParam(required = false) @Nullable List<String> accountType,
      @RequestParam(required = false) @Nullable List<String> account,
      @RequestParam(required = false) @Nullable List<String> balanceAccount,
      Model model) {
    List<String> types = chosen(accountType);
    List<String> codes = chosen(account);
    List<String> registers = chosen(balanceAccount);
    List<RegisterBalance> found = balances.findBalances(types, codes, registers);
    model.addAttribute("tab", "balances");
    model.addAttribute("accountTypes", balances.findAccountTypeCodes());
    model.addAttribute("accounts", balances.findAccountCodes(types));
    model.addAttribute("balanceAccounts", balances.findRegisterTypeCodes());
    model.addAttribute("chosenAccountTypes", types);
    model.addAttribute("chosenAccounts", codes);
    model.addAttribute("chosenBalanceAccounts", registers);
    model.addAttribute(
        "summary",
        Totals.perKey(found, balance -> balance.registerType() + "  " + balance.currency()));
    model.addAttribute("rows", found.stream().map(BalanceRow::of).toList());
    model.addAttribute("totals", Totals.perKey(found, RegisterBalance::currency));
    return "balances";
  }

  private static List<String> chosen(@Nullable List<String> values) {
    return values == null ? List.of() : values.stream().filter(value -> !value.isBlank()).toList();
  }

  /** One register as the table shows it. */
  public record BalanceRow(
      String accountType,
      String accountCode,
      String accountName,
      String balanceAccount,
      String currency,
      String debits,
      String credits,
      String balance) {
    static BalanceRow of(RegisterBalance balance) {
      return new BalanceRow(
          balance.accountType(),
          balance.accountCode(),
          balance.accountName(),
          balance.registerType(),
          balance.currency(),
          Money.format(balance.debits()),
          Money.format(balance.credits()),
          Money.formatSide(balance.net()));
    }
  }

  /** Debits, credits, and balance summed under one label, such as a currency. */
  public record Totals(String label, String debits, String credits, String balance) {
    static List<Totals> perKey(
        List<RegisterBalance> balances, Function<RegisterBalance, String> key) {
      Map<String, long[]> sums = new LinkedHashMap<>();
      for (RegisterBalance balance : balances) {
        long[] sum = sums.computeIfAbsent(key.apply(balance), unused -> new long[2]);
        sum[0] += balance.debits();
        sum[1] += balance.credits();
      }
      return sums.entrySet().stream()
          .map(
              entry ->
                  new Totals(
                      entry.getKey(),
                      Money.format(entry.getValue()[0]),
                      Money.format(entry.getValue()[1]),
                      Money.formatSide(entry.getValue()[0] - entry.getValue()[1])))
          .toList();
    }
  }
}
