package com.outpost.pspsimulator;

import com.outpost.pspsimulator.refund.RefundCommand;
import com.outpost.pspsimulator.refund.RefundLine;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A refund request: the order's PSP and payment references, the caller's refund reference, the
 * amount to refund in minor units with its currency, and the refunded lines the webhook echoes.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefundRequest(
    String pspReference,
    String paymentReference,
    String refundReference,
    long amount,
    String currency,
    @Nullable List<RefundLine> refundLines) {

  /** Returns the request as a refund command. */
  public RefundCommand toCommand() {
    return new RefundCommand(
        pspReference,
        paymentReference,
        refundReference,
        amount,
        currency,
        refundLines == null ? List.of() : refundLines);
  }
}
