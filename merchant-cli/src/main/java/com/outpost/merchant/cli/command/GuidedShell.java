package com.outpost.merchant.cli.command;

import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.configuration.MerchantCliProperties.CatalogueItem;
import com.outpost.merchant.cli.merchant.Merchant;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.merchant.Psp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Walks a merchant through the platform step by step: pick the merchant, pick what to do, answer
 * the questions that action needs, see the result, and start over. Every action is one of the
 * shell's commands, so the guided path and the scripted path do the same thing.
 */
public final class GuidedShell {
  private static final List<String> ACTIONS =
      List.of(
          "create an order",
          "pay an order",
          "refund an order",
          "today's balance report for this merchant",
          "today's balance report for the platform (operator)",
          "switch merchant");
  private static final List<String> CARDS =
      List.of(
          OrderCommands.APPROVED_CARD + "  approved",
          "4000000000000002  refused by the acquirer",
          "4000000000000009  scheme error");

  private final BufferedReader in;
  private final PrintWriter out;
  private final MerchantRepository merchants;
  private final ShellSession session;
  private final List<CatalogueItem> catalogue;
  private final MerchantCommands merchantCommands;
  private final OrderCommands orderCommands;
  private @Nullable String lastOrderReference;

  /** Creates the guided shell over the terminal's input and output. */
  public GuidedShell(
      BufferedReader in,
      PrintWriter out,
      MerchantRepository merchants,
      ShellSession session,
      MerchantCliProperties properties,
      MerchantCommands merchantCommands,
      OrderCommands orderCommands) {
    this.in = in;
    this.out = out;
    this.merchants = merchants;
    this.session = session;
    this.catalogue = properties.catalogue();
    this.merchantCommands = merchantCommands;
    this.orderCommands = orderCommands;
  }

  /** Runs until the merchant quits or the input ends. */
  public void run() {
    out.println("Outpost merchant shell. Answer with the number of a choice; 0 quits.");
    if (!chooseMerchant()) {
      return;
    }
    while (true) {
      Optional<Integer> action = chooseOne("What next?", ACTIONS);
      if (action.isEmpty()) {
        out.println("Bye.");
        return;
      }
      boolean continued =
          switch (action.orElseThrow()) {
            case 0 -> order();
            case 1 -> pay();
            case 2 -> refund();
            case 3 -> print(merchantCommands.report(today(), today()));
            case 4 -> print(merchantCommands.reportPlatform(today(), today()));
            default -> chooseMerchant();
          };
      if (!continued) {
        return;
      }
    }
  }

  private boolean chooseMerchant() {
    List<Merchant> active = merchants.findActiveMerchants();
    List<Merchant> usable =
        active.stream().filter(merchant -> session.hasCredentials(merchant.code())).toList();
    if (usable.isEmpty()) {
      out.println("No active merchant has configured credentials; see merchant.cli.merchants.");
      return false;
    }
    Optional<Integer> chosen =
        chooseOne(
            "Which merchant?",
            usable.stream().map(merchant -> merchant.code() + "  " + merchant.name()).toList());
    if (chosen.isEmpty()) {
      out.println("Bye.");
      return false;
    }
    out.println(merchantCommands.use(usable.get(chosen.orElseThrow()).code()));
    return true;
  }

  private boolean order() {
    List<Psp> psps = merchants.findEnabledPsps(session.merchantCode());
    if (psps.isEmpty()) {
      return print("no PSP enabled for " + session.merchantCode());
    }
    Optional<Integer> psp =
        chooseOne(
            "Which PSP?",
            psps.stream().map(candidate -> candidate.code() + "  " + candidate.name()).toList());
    if (psp.isEmpty()) {
      return true;
    }
    Optional<String> country = ask("Shopper country (ISO code)", "NL");
    if (country.isEmpty()) {
      return true;
    }
    Optional<String> state = ask("Shopper state, e.g. US-CA (blank for none)", "");
    if (state.isEmpty()) {
      return true;
    }
    Optional<List<Integer>> items =
        chooseMany(
            "Which items? Several as 1,3",
            catalogue.stream()
                .map(
                    item ->
                        item.sku()
                            + "  "
                            + item.name()
                            + "  "
                            + Money.format(item.amount(), item.currency()))
                .toList());
    if (items.isEmpty()) {
      return true;
    }
    String skus =
        items.orElseThrow().stream()
            .map(index -> catalogue.get(index).sku())
            .collect(Collectors.joining(","));
    String printed =
        orderCommands.order(
            psps.get(psp.orElseThrow()).code(),
            skus,
            country.orElseThrow(),
            state.orElseThrow().isBlank() ? null : state.orElseThrow(),
            "shopper@example.test",
            "Test Shopper");
    if (printed.startsWith("order ")) {
      lastOrderReference = printed.substring("order ".length(), printed.indexOf('\n'));
    }
    return print(printed);
  }

  private boolean pay() {
    Optional<String> reference = askOrderReference();
    if (reference.isEmpty()) {
      return true;
    }
    Optional<Integer> card = chooseOne("Which test card?", CARDS);
    if (card.isEmpty()) {
      return true;
    }
    String cardNumber = CARDS.get(card.orElseThrow()).split("  ", -1)[0];
    return print(orderCommands.pay(reference.orElseThrow(), cardNumber));
  }

  private boolean refund() {
    Optional<String> reference = askOrderReference();
    if (reference.isEmpty()) {
      return true;
    }
    return print(orderCommands.refund(reference.orElseThrow()));
  }

  private Optional<String> askOrderReference() {
    String fallback = lastOrderReference == null ? "" : lastOrderReference;
    Optional<String> reference = ask("Order reference", fallback);
    if (reference.isPresent() && reference.orElseThrow().isBlank()) {
      out.println("No order yet; create one first.");
      return Optional.empty();
    }
    return reference;
  }

  private boolean print(String text) {
    out.println(text);
    out.println();
    return true;
  }

  /** Today's date in UTC, the calendar the reports are read in. */
  private static String today() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  /** Asks until the answer is one option's number; empty means quit or step back. */
  private Optional<Integer> chooseOne(String question, List<String> options) {
    return chooseMany(question, options, false).map(List::getFirst);
  }

  private Optional<List<Integer>> chooseMany(String question, List<String> options) {
    return chooseMany(question, options, true);
  }

  private Optional<List<Integer>> chooseMany(String question, List<String> options, boolean many) {
    while (true) {
      out.println(question);
      for (int index = 0; index < options.size(); index++) {
        out.println("  " + (index + 1) + ") " + options.get(index));
      }
      out.print("> ");
      out.flush();
      String answer = readLine();
      if (answer == null || answer.trim().equals("0")) {
        return Optional.empty();
      }
      Optional<List<Integer>> chosen = parseChoices(answer, options.size(), many);
      if (chosen.isPresent()) {
        return chosen;
      }
      out.println(
          many
              ? "Answer with numbers between 1 and " + options.size() + ", separated by commas."
              : "Answer with a number between 1 and " + options.size() + ".");
    }
  }

  private static Optional<List<Integer>> parseChoices(String answer, int size, boolean many) {
    List<Integer> chosen = new ArrayList<>();
    for (String part : answer.split(",", -1)) {
      int number;
      try {
        number = Integer.parseInt(part.trim());
      } catch (NumberFormatException notNumber) {
        return Optional.empty();
      }
      if (number < 1 || number > size || (!many && !chosen.isEmpty())) {
        return Optional.empty();
      }
      if (!chosen.contains(number - 1)) {
        chosen.add(number - 1);
      }
    }
    return chosen.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(chosen));
  }

  /** Asks a free-text question; an empty answer takes the default, and end of input quits. */
  private Optional<String> ask(String question, String fallback) {
    out.print(question + (fallback.isEmpty() ? "" : " [" + fallback + "]") + ": ");
    out.flush();
    String answer = readLine();
    if (answer == null) {
      return Optional.empty();
    }
    return Optional.of(answer.isBlank() ? fallback : answer.trim());
  }

  private @Nullable String readLine() {
    try {
      return in.readLine();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
