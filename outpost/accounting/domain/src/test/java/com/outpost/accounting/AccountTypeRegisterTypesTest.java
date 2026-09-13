package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.AccountTypeRegisterTypes.AccountTypeRegisterType;
import com.outpost.accounting.RegisterTypes.RegisterType;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountTypeRegisterTypesTest {

  @Test
  void definesThePermittedAccountTypeRegisterTypePairs() {
    assertEquals(
        Arrays.asList(
            new Pair(1L, "MERCHANT", "MERCHANT_PAYABLE"),
            new Pair(2L, "BANK_ACCOUNT", "PAYOUT_PAYABLE"),
            new Pair(3L, "PSP", "PSP_RECEIVABLE"),
            new Pair(4L, "TAX_AUTHORITY", "TAX_PAYABLE"),
            new Pair(5L, "PLATFORM", "FEE_REVENUE"),
            new Pair(6L, "PLATFORM", "FX_CLEARING"),
            new Pair(7L, "PLATFORM", "FX_FEE_REVENUE"),
            new Pair(8L, "MERCHANT", "PENDING_FEE"),
            new Pair(9L, "PLATFORM", "PENDING_FEE")),
        Arrays.stream(AccountTypeRegisterTypes.values())
            .map(AccountTypeRegisterTypesTest::pair)
            .toList());
  }

  @Test
  void claimsEveryRegisterTypeAndLeavesRootUnclaimed() {
    Set<RegisterType> claimedRegisterTypes =
        Arrays.stream(AccountTypeRegisterTypes.values())
            .map(constant -> constant.getValue().getRegisterType())
            .collect(Collectors.toSet());

    assertEquals(
        Arrays.stream(RegisterTypes.values())
            .map(RegisterTypes::getValue)
            .collect(Collectors.toSet()),
        claimedRegisterTypes);
    assertFalse(
        Arrays.stream(AccountTypeRegisterTypes.values())
            .anyMatch(
                constant ->
                    constant.getValue().getAccountType().equals(AccountTypes.ROOT.getValue())));
  }

  @Test
  void allowsSeveralRegisterTypesForMerchantAndPlatform() {
    assertEquals(
        2,
        Arrays.stream(AccountTypeRegisterTypes.values())
            .filter(
                constant ->
                    constant.getValue().getAccountType().equals(AccountTypes.MERCHANT.getValue()))
            .count());
    assertEquals(
        4,
        Arrays.stream(AccountTypeRegisterTypes.values())
            .filter(
                constant ->
                    constant.getValue().getAccountType().equals(AccountTypes.PLATFORM.getValue()))
            .count());
  }

  @Test
  void findsThePairOfAnAccountTypeAndRegisterType() {
    assertEquals(
        Optional.of(AccountTypeRegisterTypes.PLATFORM_PENDING_FEE),
        AccountTypeRegisterTypes.fromAccountTypeAndRegisterType(
            AccountTypes.PLATFORM.getValue(), RegisterTypes.PENDING_FEE.getValue()));
    assertEquals(
        Optional.empty(),
        AccountTypeRegisterTypes.fromAccountTypeAndRegisterType(
            AccountTypes.PSP.getValue(), RegisterTypes.PENDING_FEE.getValue()));
  }

  @Test
  void payablesAreCreditNormalAndReceivablesDebitNormal() {
    assertEquals(
        NormalBalances.CREDIT, AccountTypeRegisterTypes.MERCHANT_MERCHANT_PAYABLE.normalBalance());
    assertEquals(NormalBalances.DEBIT, AccountTypeRegisterTypes.PSP_PSP_RECEIVABLE.normalBalance());
    assertEquals(
        NormalBalances.DEBIT, AccountTypeRegisterTypes.PLATFORM_FX_CLEARING.normalBalance());
  }

  private static Pair pair(AccountTypeRegisterTypes constant) {
    AccountTypeRegisterType value = constant.getValue();
    return new Pair(
        value.getAccountTypeRegisterTypeId(),
        value.getAccountType().getCode(),
        value.getRegisterType().getCode());
  }

  private record Pair(long id, String accountTypeCode, String registerTypeCode) {}
}
