package com.outpost.integration.psp;

import com.outpost.payment.common.Amount;
import java.math.BigDecimal;

/**
 * One refunded order line as the PSP receives and echoes it; the PSP does not act on it.
 *
 * @param taxRate the rate applied to the line when the order was priced
 * @param netAmount the line's net amount
 * @param grossAmount the line's net plus tax
 */
public record RefundPspOrderLine(
    String orderLineReference, BigDecimal taxRate, Amount netAmount, Amount grossAmount) {}
