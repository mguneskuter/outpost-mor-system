package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * JSON request for creating a payment order. Each constraint carries the error code the request is
 * refused with; the bounds are conservative limits on merchant references and order size.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateOrderRequest(
    @NotBlank(message = INVALID_MERCHANT_REFERENCE)
        @Size(max = REFERENCE_MAX_LENGTH, message = INVALID_MERCHANT_REFERENCE)
        String merchantReference,
    @NotBlank(message = INVALID_IDEMPOTENCY_KEY)
        @Size(max = REFERENCE_MAX_LENGTH, message = INVALID_IDEMPOTENCY_KEY)
        String idempotencyKey,
    @NotNull(message = INVALID_SHOPPER_DETAILS) @Valid ShopperDetails shopperDetails,
    @NotBlank(message = INVALID_PSP_CODE) String pspCode,
    @NotNull(message = INVALID_ORDER_DETAILS) @Valid OrderDetails orderDetails) {
  /** The longest merchant-supplied reference or key accepted. */
  public static final int REFERENCE_MAX_LENGTH = 128;

  /** The longest shopper name or email address accepted. */
  public static final int SHOPPER_TEXT_MAX_LENGTH = 254;

  /** The most order lines one order may carry. */
  public static final int ORDER_LINES_MAX_SIZE = 100;

  public static final String INVALID_MERCHANT_REFERENCE = "INVALID_MERCHANT_REFERENCE";
  public static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
  public static final String INVALID_SHOPPER_DETAILS = "INVALID_SHOPPER_DETAILS";
  public static final String INVALID_PSP_CODE = "INVALID_PSP_CODE";
  public static final String INVALID_ORDER_DETAILS = "INVALID_ORDER_DETAILS";

  /** Converts the validated transport payload to an application command. */
  public CreateOrderCommand toCommand() {
    return new CreateOrderCommand(
        merchantReference,
        idempotencyKey,
        new ShopperDetailsCommand(
            shopperDetails.fullName(),
            shopperDetails.email(),
            shopperDetails.country(),
            shopperDetails.state(),
            shopperDetails.zipcode()),
        pspCode,
        new OrderDetailsCommand(
            orderDetails.orderLines().stream()
                .map(
                    line ->
                        new OrderLineCommand(
                            line.merchantLineReference(),
                            line.amount(),
                            line.currency(),
                            line.type()))
                .toList(),
            orderDetails.totalAmount(),
            orderDetails.currency()));
  }

  /** Shopper information supplied for tax calculation and payment creation. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ShopperDetails(
      @NotBlank(message = INVALID_SHOPPER_DETAILS_FULL_NAME)
          @Size(max = SHOPPER_TEXT_MAX_LENGTH, message = INVALID_SHOPPER_DETAILS_FULL_NAME)
          String fullName,
      @NotBlank(message = INVALID_SHOPPER_DETAILS_EMAIL)
          @Size(max = SHOPPER_TEXT_MAX_LENGTH, message = INVALID_SHOPPER_DETAILS_EMAIL)
          String email,
      @NotBlank(message = INVALID_SHOPPER_DETAILS_COUNTRY) String country,
      @Nullable String state,
      @Nullable String zipcode) {
    public static final String INVALID_SHOPPER_DETAILS_FULL_NAME =
        "INVALID_SHOPPER_DETAILS_FULL_NAME";
    public static final String INVALID_SHOPPER_DETAILS_EMAIL = "INVALID_SHOPPER_DETAILS_EMAIL";
    public static final String INVALID_SHOPPER_DETAILS_COUNTRY = "INVALID_SHOPPER_DETAILS_COUNTRY";
  }

  /** Order totals and merchant line inputs. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record OrderDetails(
      @NotNull(message = INVALID_ORDER_LINES)
          @Size(min = 1, max = ORDER_LINES_MAX_SIZE, message = INVALID_ORDER_LINES)
          List<@NotNull(message = INVALID_ORDER_LINE) @Valid OrderLine> orderLines,
      @NotNull(message = INVALID_ORDER_DETAILS_TOTAL_AMOUNT)
          @Positive(message = INVALID_ORDER_DETAILS_TOTAL_AMOUNT)
          Long totalAmount,
      @NotBlank(message = INVALID_ORDER_DETAILS_CURRENCY) String currency) {
    public static final String INVALID_ORDER_LINES = "INVALID_ORDER_LINES";
    public static final String INVALID_ORDER_LINE = "INVALID_ORDER_LINE";
    public static final String INVALID_ORDER_DETAILS_TOTAL_AMOUNT =
        "INVALID_ORDER_DETAILS_TOTAL_AMOUNT";
    public static final String INVALID_ORDER_DETAILS_CURRENCY = "INVALID_ORDER_DETAILS_CURRENCY";
  }

  /** One net-priced order line. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record OrderLine(
      @NotBlank(message = INVALID_MERCHANT_LINE_REFERENCE)
          @Size(max = REFERENCE_MAX_LENGTH, message = INVALID_MERCHANT_LINE_REFERENCE)
          String merchantLineReference,
      @NotNull(message = INVALID_ORDER_LINES_AMOUNT) @Positive(message = INVALID_ORDER_LINES_AMOUNT)
          Long amount,
      @NotBlank(message = INVALID_ORDER_LINES_CURRENCY) String currency,
      @NotBlank(message = INVALID_ORDER_LINES_TYPE) String type) {
    public static final String INVALID_MERCHANT_LINE_REFERENCE = "INVALID_MERCHANT_LINE_REFERENCE";
    public static final String INVALID_ORDER_LINES_AMOUNT = "INVALID_ORDER_LINES_AMOUNT";
    public static final String INVALID_ORDER_LINES_CURRENCY = "INVALID_ORDER_LINES_CURRENCY";
    public static final String INVALID_ORDER_LINES_TYPE = "INVALID_ORDER_LINES_TYPE";
  }
}
