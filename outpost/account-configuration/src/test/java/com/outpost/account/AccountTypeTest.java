package com.outpost.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountTypeTest {

  @Test
  void containsExactlySixApprovedValues() {
    Set<AccountType> actual =
        Arrays.stream(AccountTypes.values()).map(AccountTypes::value).collect(Collectors.toSet());
    assertEquals(6, actual.size());
  }

  @Test
  void typeIdsAndCodesAreDistinct() {
    long distinctIds =
        Arrays.stream(AccountTypes.values())
            .map(AccountTypes::value)
            .map(AccountType::accountTypeId)
            .distinct()
            .count();
    long distinctCodes =
        Arrays.stream(AccountTypes.values())
            .map(AccountTypes::value)
            .map(AccountType::code)
            .distinct()
            .count();
    assertEquals(6, distinctIds);
    assertEquals(6, distinctCodes);
  }

  @Test
  void lookupResolvesEverySupportedCodeToItsOwnValue() {
    for (AccountTypes constant : AccountTypes.values()) {
      String code = constant.value().code();
      Optional<AccountType> found = AccountTypes.fromCode(code);
      assertTrue(found.isPresent());
      assertEquals(constant.value(), found.orElseThrow());
    }
  }

  @Test
  void lookupIsCaseSensitiveAndUntrimmed() {
    assertTrue(AccountTypes.fromCode("ROOT").isPresent());
    assertFalse(AccountTypes.fromCode("root").isPresent());
    assertFalse(AccountTypes.fromCode("ROOT ").isPresent());
    assertFalse(AccountTypes.fromCode(" ROOT").isPresent());
    assertFalse(AccountTypes.fromCode("UNKNOWN").isPresent());
    assertFalse(AccountTypes.fromCode("MER_CHANT").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void lookupRejectsNullCode() {
    assertThrows(NullPointerException.class, () -> AccountTypes.fromCode((String) null));
  }
}
