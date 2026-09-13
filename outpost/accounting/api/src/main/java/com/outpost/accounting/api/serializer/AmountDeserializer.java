package com.outpost.accounting.api.serializer;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads an amount from {@code {"quantity": <minor units>, "currency": <ISO 4217 code>}}; any other
 * shape or an unknown currency fails deserialization.
 */
public final class AmountDeserializer extends ValueDeserializer<Amount> {
  private static final String ERROR_MESSAGE =
      "amount must be {\"quantity\": <minor units>, \"currency\": <ISO 4217 code>}";

  @Override
  public Amount deserialize(JsonParser parser, DeserializationContext context) {
    JsonNode node = context.readTree(parser);
    @Nullable JsonNode quantity = node.get("quantity");
    @Nullable JsonNode currencyCode = node.get("currency");
    if (!node.isObject()
        || quantity == null
        || !quantity.canConvertToExactIntegral()
        || currencyCode == null
        || !currencyCode.isString()) {
      return context.reportInputMismatch(this, ERROR_MESSAGE);
    }
    return Currencies.fromCurrencyCode(currencyCode.stringValue())
        .map(currency -> new Amount(currency, quantity.longValue()))
        .orElseGet(() -> context.reportInputMismatch(this, ERROR_MESSAGE));
  }
}
