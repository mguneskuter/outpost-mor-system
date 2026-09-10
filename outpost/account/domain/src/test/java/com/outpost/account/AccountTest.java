package com.outpost.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.account.AccountTypes.AccountType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private static List<AccountType> allAccountTypes() {
    return Arrays.stream(AccountTypes.values())
        .map(AccountTypes::getValue)
        .collect(Collectors.toList());
  }

  private static Account root(long id) {
    return Account.of(
        id, AccountTypes.ROOT.getValue(), "ROOT" + id, "Root", true, CREATED_AT, null);
  }

  /** Returns an account of {@code type} placed under a legal parent so it is constructible. */
  private static Account parentOf(AccountType type) {
    if (type.equals(AccountTypes.ROOT.getValue())) {
      return root(1L);
    }
    Account anchoringRoot = root(2L);
    if (type.equals(AccountTypes.BANK_ACCOUNT.getValue())) {
      Account merchant =
          Account.of(
              3L,
              AccountTypes.MERCHANT.getValue(),
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
  void rejectsNonPositiveId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                0L,
                AccountTypes.MERCHANT.getValue(),
                "M1",
                "Merchant",
                true,
                CREATED_AT,
                root(1L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                -1L,
                AccountTypes.MERCHANT.getValue(),
                "M1",
                "Merchant",
                true,
                CREATED_AT,
                root(1L)));
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
                AccountTypes.MERCHANT.getValue(),
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
                1L, AccountTypes.MERCHANT.getValue(), "", "Merchant", true, CREATED_AT, root(2L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.getValue(),
                "   ",
                "Merchant",
                true,
                CREATED_AT,
                root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullName() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.getValue(),
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
        () ->
            Account.of(1L, AccountTypes.MERCHANT.getValue(), "M1", "", true, CREATED_AT, root(2L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L, AccountTypes.MERCHANT.getValue(), "M1", "   ", true, CREATED_AT, root(2L)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCreatedAt() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L,
                AccountTypes.MERCHANT.getValue(),
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
                1L, AccountTypes.MERCHANT.getValue(), "M1", "Merchant", true, CREATED_AT, null));
  }

  @Test
  void rootAccountCannotHaveParent() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Account.of(
                1L, AccountTypes.ROOT.getValue(), "ROOT1", "Root", true, CREATED_AT, root(2L)));
  }

  @Test
  void everyAllowedParentChildPairIsAccepted() {
    for (AccountType parentType : allAccountTypes()) {
      Account parent = parentOf(parentType);
      for (AccountType childType : AccountTypes.getAllowedChildrenTypesForParent(parentType)) {
        Account child = Account.of(2L, childType, "CODE", "Name", true, CREATED_AT, parent);
        assertTrue(parent.getChildAccounts().contains(child));
        assertEquals(parent, child.getParentAccount().orElse(null));
      }
    }
  }

  @Test
  void everyDisallowedParentChildPairIsRejected() {
    for (AccountType parentType : allAccountTypes()) {
      for (AccountType childType : allAccountTypes()) {
        if (parentType.isAllowedChildrenType(childType)) {
          continue;
        }
        Account parent = parentOf(parentType);
        assertThrows(
            IllegalArgumentException.class,
            () -> Account.of(2L, childType, "CODE", "Name", true, CREATED_AT, parent),
            parentType.getCode() + " must not allow " + childType.getCode() + " as a child");
      }
    }
  }

  @Test
  void successfulCreationLinksBothDirections() {
    Account root = root(1L);
    Account merchant =
        Account.of(2L, AccountTypes.MERCHANT.getValue(), "M2", "Merchant", true, CREATED_AT, root);
    Account bankAccount =
        Account.of(
            3L, AccountTypes.BANK_ACCOUNT.getValue(), "B3", "Bank", true, CREATED_AT, merchant);
    assertEquals(root, merchant.getParentAccount().orElse(null));
    assertEquals(merchant, bankAccount.getParentAccount().orElse(null));
    assertEquals(List.of(merchant), root.getChildAccounts());
    assertEquals(List.of(bankAccount), merchant.getChildAccounts());
  }

  @Test
  void childAccountsAreImmutableFromCallers() {
    Account root = root(1L);
    Account.of(2L, AccountTypes.MERCHANT.getValue(), "M2", "Merchant", true, CREATED_AT, root);
    List<Account> children = root.getChildAccounts();
    assertThrows(UnsupportedOperationException.class, () -> children.add(root));
  }

  @Test
  void parentAccessThrowsWhenNonRootParentMissing() {
    Account root = root(1L);
    Account merchant =
        Account.of(2L, AccountTypes.MERCHANT.getValue(), "M2", "Merchant", true, CREATED_AT, root);
    clearParentLink(merchant);
    assertThrows(IllegalStateException.class, merchant::getParentAccount);
  }

  @Test
  void toStringDoesNotTraverseLinks() {
    Account root = root(1L);
    Account.of(2L, AccountTypes.MERCHANT.getValue(), "M2", "Merchant", true, CREATED_AT, root);
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
