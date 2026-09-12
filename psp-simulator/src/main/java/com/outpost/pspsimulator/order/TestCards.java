package com.outpost.pspsimulator.order;

import java.util.Map;
import java.util.Optional;

/**
 * The documented test card numbers and the outcome each selects.
 *
 * <ul>
 *   <li>{@code 4111111111111111} is approved;
 *   <li>{@code 4000000000000002} is refused by the acquirer;
 *   <li>{@code 4000000000000009} fails at the scheme.
 * </ul>
 *
 * <p>Any other number is not a documented test card.
 */
public enum TestCards {
  APPROVED("4111111111111111", ResultCodes.APPROVED),
  ACQUIRER_REFUSED("4000000000000002", ResultCodes.ACQUIRER_REFUSED),
  SCHEME_ERROR("4000000000000009", ResultCodes.SCHEME_ERROR);

  private static final Map<String, ResultCodes> OUTCOMES_BY_CARD =
      Map.of(
          APPROVED.cardNumber, APPROVED.outcome,
          ACQUIRER_REFUSED.cardNumber, ACQUIRER_REFUSED.outcome,
          SCHEME_ERROR.cardNumber, SCHEME_ERROR.outcome);

  private final String cardNumber;
  private final ResultCodes outcome;

  TestCards(String cardNumber, ResultCodes outcome) {
    this.cardNumber = cardNumber;
    this.outcome = outcome;
  }

  /** Returns the outcome a card number selects, if it is a documented test card. */
  public static Optional<ResultCodes> outcomeFor(String cardNumber) {
    return Optional.ofNullable(OUTCOMES_BY_CARD.get(cardNumber));
  }
}
