package com.outpost.accounting.api.serializer;

import com.outpost.payment.common.Amount;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes an amount as {@code {"quantity": <minor units>, "currency": <ISO 4217 code>}}. */
public final class AmountSerializer extends ValueSerializer<Amount> {
  @Override
  public void serialize(Amount value, JsonGenerator generator, SerializationContext context) {
    generator.writeStartObject();
    generator.writeNumberProperty("quantity", value.quantity());
    generator.writeStringProperty("currency", value.currency().getCurrencyCode());
    generator.writeEndObject();
  }
}
