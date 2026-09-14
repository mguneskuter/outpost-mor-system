package com.outpost.accounting.journalentry;

import com.outpost.accounting.Register;
import com.outpost.payment.common.Amount;

/**
 * The fee a payment's FEE_PENDING entry holds, with the merchant and platform PENDING_FEE registers
 * it holds the fee on.
 */
public record PendingFee(
    Amount fee, Register merchantPendingFeeRegister, Register platformPendingFeeRegister) {}
