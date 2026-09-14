package com.outpost.accounting.journalentry;

import com.outpost.accounting.Register;

/**
 * The registers a CAPTURE entry posts the payment to; a refund of the payment reverses the first
 * three.
 */
public record CaptureRegisters(
    Register pspReceivableRegister,
    Register taxPayableRegister,
    Register merchantPayableRegister,
    Register feeRevenueRegister) {}
