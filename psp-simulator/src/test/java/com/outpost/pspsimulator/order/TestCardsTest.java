package com.outpost.pspsimulator.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestCardsTest {

  @ParameterizedTest
  @CsvSource({
    "4111111111111111,APPROVED",
    "4000000000000002,ACQUIRER_REFUSED",
    "4000000000000009,SCHEME_ERROR"
  })
  void selectsTheDocumentedOutcomeForEachCard(String cardNumber, ResultCodes expected) {
    assertThat(TestCards.outcomeFor(cardNumber)).hasValue(expected);
  }

  @Test
  void rejectsNumbersThatAreNotDocumentedTestCards() {
    assertThat(TestCards.outcomeFor("1234567890123456")).isEmpty();
  }
}
