package com.outpost.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.account.AccountTypes.AccountType;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private static final Map<AccountType, Set<AccountType>> ALLOWED_CHILDREN =
      Map.of(
          AccountTypes.ROOT.value(),
              setOf(
                  AccountTypes.MERCHANT.value(),
                  AccountTypes.PSP.value(),
                  AccountTypes.PLATFORM.value(),
                  AccountTypes.TAX_AUTHORITY.value()),
          AccountTypes.MERCHANT.value(), setOf(AccountTypes.BANK_ACCOUNT.value()),
          AccountTypes.PSP.value(), setOf(AccountTypes.BANK_ACCOUNT.value()),
          AccountTypes.PLATFORM.value(), setOf(AccountTypes.BANK_ACCOUNT.value()),
          AccountTypes.TAX_AUTHORITY.value(), setOf(AccountTypes.BANK_ACCOUNT.value()),
          AccountTypes.BANK_ACCOUNT.value(), Set.of());

  private static Set<AccountType> setOf(AccountType... types) {
    return new HashSet<>(Arrays.asList(types));
  }

  private static List<AccountType> allAccountTypes() {
    return Arrays.stream(AccountTypes.values())
        .map(AccountTypes::value)
        .collect(Collectors.toList());
  }

  private static Account root(long id) {
    return Account.of(id, AccountTypes.ROOT.value(), "ROOT" + id, "Root", true, CREATED_AT, null);
  }

  /** Returns an account of {@code type} placed under a legal parent so it is constructible. */
  private static Account parentOf(AccountType type) {
    if (type.equals(AccountTypes.ROOT.value())) {
      return root(1L);
    }
    Account anchoringRoot = root(2L);
    if (type.equals(AccountTypes.BANK_ACCOUNT.value())) {
      Account merchant =
          Account.of(
              3L,
              AccountTypes.MERCHANT.value(),
              "PARENT",
              "Parent",
              true,
              CREATED_AT,
              anchoringRoot);
      return Account.of(1L, type, "PARENT", "Parent", true, CREATED_AT, merchant);
    }
    return Account.of(1L, type, "PARENT", "Parent", true, CREATED_AT, anchoringRoot);
  }

  @Test
  void rootHasNoParent() {
    Account root = root(1L);
    assertTrue(root.parentAccount().isEmpty());
    assertEquals(AccountTypes.ROOT.value(), root.accountType());
    assertTrue(root.childAccounts().isEmpty());
  }

  @Test
  void rejectsNonPositiveId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                0L, AccountTypes.MERCHANT.value(), "M1", "Merchant", true, CREATED_AT, root(1L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                -1L, AccountTypes.MERCHANT.value(), "M1", "Merchant", true, CREATED_AT, root(1L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullAccountType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Account.of(1L, (AccountType) null, "M1", "Merchant", true, CREATED_AT, root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.value(),
                (String) null,
                "Merchant",
                true,
                CREATED_AT,
                root(2L)));
  }

  @Test
  void rejectsBlankCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L, AccountTypes.MERCHANT.value(), "", "Merchant", true, CREATED_AT, root(2L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L, AccountTypes.MERCHANT.value(), "   ", "Merchant", true, CREATED_AT, root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullName() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.value(),
                "M1",
                (String) null,
                true,
                CREATED_AT,
                root(2L)));
  }

  @Test
  void rejectsBlankName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Account.of(1L, AccountTypes.MERCHANT.value(), "M1", "", true, CREATED_AT, root(2L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(1L, AccountTypes.MERCHANT.value(), "M1", "   ", true, CREATED_AT, root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCreatedAt() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.value(),
                "M1",
                "Merchant",
                true,
                (Instant) null,
                root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void nonRootAccountRequiresParent() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L, AccountTypes.MERCHANT.value(), "M1", "Merchant", true, CREATED_AT, null));
  }

  @Test
  void rootAccountCannotHaveParent() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(1L, AccountTypes.ROOT.value(), "ROOT1", "Root", true, CREATED_AT, root(2L)));
  }

  @Test
  void preservesExactScalarInput() {
    Account root = root(1L);
    Account account =
        Account.of(
            2L, AccountTypes.MERCHANT.value(), "  M2  ", "  Merchant  ", false, CREATED_AT, root);
    assertEquals("  M2  ", account.code());
    assertEquals("  Merchant  ", account.name());
    assertEquals(AccountTypes.MERCHANT.value(), account.accountType());
    assertEquals(2L, account.accountId());
    assertFalse(account.active());
    assertEquals(CREATED_AT, account.createdAt());
  }

  @Test
  void everyAllowedParentChildPairIsAccepted() {
    for (AccountType parentType : allAccountTypes()) {
      Account parent = parentOf(parentType);
      for (AccountType childType : ALLOWED_CHILDREN.getOrDefault(parentType, Set.of())) {
        Account child = Account.of(2L, childType, "CODE", "Name", true, CREATED_AT, parent);
        assertTrue(parent.childAccounts().contains(child));
        assertEquals(parent, child.parentAccount().orElse(null));
      }
    }
  }

  @Test
  void everyDisallowedParentChildPairIsRejected() {
    for (AccountType parentType : allAccountTypes()) {
      for (AccountType childType : allAccountTypes()) {
        if (ALLOWED_CHILDREN.getOrDefault(parentType, Set.of()).contains(childType)) {
          continue;
        }
        Account parent = parentOf(parentType);
        assertThrows(
            IllegalArgumentException.class,
            () -> Account.of(2L, childType, "CODE", "Name", true, CREATED_AT, parent),
            parentType.code() + " must not allow " + childType.code() + " as a child");
      }
    }
  }

  @Test
  void successfulCreationLinksBothDirections() {
    Account root = root(1L);
    Account merchant =
        Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    Account bankAccount =
        Account.of(3L, AccountTypes.BANK_ACCOUNT.value(), "B3", "Bank", true, CREATED_AT, merchant);
    assertEquals(root, merchant.parentAccount().orElse(null));
    assertEquals(merchant, bankAccount.parentAccount().orElse(null));
    assertEquals(List.of(merchant), root.childAccounts());
    assertEquals(List.of(bankAccount), merchant.childAccounts());
  }

  @Test
  @SuppressWarnings("NullAway")
  void equalityUsesOnlyAccountId() {
    Account root = root(1L);
    Account first =
        Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    Account sameIdDifferentScalars =
        Account.of(2L, AccountTypes.PSP.value(), "P2", "Psp", true, CREATED_AT, root);
    Account differentId =
        Account.of(3L, AccountTypes.MERCHANT.value(), "M3", "Merchant", true, CREATED_AT, root);

    assertEquals(first, sameIdDifferentScalars);
    assertEquals(first.hashCode(), sameIdDifferentScalars.hashCode());
    assertNotEquals(first, differentId);
    assertFalse(first.equals(null));
  }

  @Test
  void childAccountsAreImmutableFromCallers() {
    Account root = root(1L);
    Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    List<Account> children = root.childAccounts();
    assertThrows(UnsupportedOperationException.class, () -> children.add(root));
  }

  @Test
  void parentIsEmptyForRoot() {
    Account root = root(1L);
    Account merchant =
        Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    assertTrue(root.parentAccount().isEmpty());
    assertEquals(root, merchant.parentAccount().orElse(null));
  }

  @Test
  void parentAccessThrowsWhenNonRootParentMissing() {
    Account root = root(1L);
    Account merchant =
        Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    clearParentLink(merchant);
    assertThrows(IllegalStateException.class, () -> merchant.parentAccount());
  }

  @Test
  void toStringDoesNotTraverseLinks() {
    Account root = root(1L);
    Account.of(2L, AccountTypes.MERCHANT.value(), "M2", "Merchant", true, CREATED_AT, root);
    String representation = root.toString();
    assertTrue(representation.contains("ROOT"));
    assertFalse(representation.contains("M2"));
  }

  @SuppressWarnings("NullAway")
  private static void clearParentLink(Account account) {
    try {
      java.lang.reflect.Field parent = Account.class.getDeclaredField("parentAccount");
      parent.setAccessible(true);
      parent.set(account, null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
