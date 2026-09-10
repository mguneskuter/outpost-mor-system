package com.outpost.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.account.AccountTypes.AccountType;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountTypeTest {

  @Test
  void typeIdsAndCodesAreDistinct() {
    long distinctIds =
        Arrays.stream(AccountTypes.values())
            .map(AccountTypes::getValue)
            .map(AccountType::getAccountTypeId)
            .distinct()
            .count();
    long distinctCodes =
        Arrays.stream(AccountTypes.values())
            .map(AccountTypes::getValue)
            .map(AccountType::getCode)
            .distinct()
            .count();
    assertEquals(AccountTypes.values().length, distinctIds);
    assertEquals(AccountTypes.values().length, distinctCodes);
  }

  @Test
  void lookupResolvesEverySupportedCodeToItsOwnValue() {
    for (AccountTypes constant : AccountTypes.values()) {
      String code = constant.getValue().getCode();
      Optional<AccountType> found = AccountTypes.fromCode(code);
      assertTrue(found.isPresent());
      assertEquals(constant.getValue(), found.orElseThrow());
    }
  }

  @Test
  @SuppressWarnings("NullAway")
  void lookupRejectsNullCode() {
    assertThrows(NullPointerException.class, () -> AccountTypes.fromCode((String) null));
  }
}
