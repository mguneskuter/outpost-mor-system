package com.outpost.accounting.api.json;

import com.outpost.common.iso.Countries.Country;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes a country as its ISO 3166-1 alpha-2 code. */
public final class CountryIsoCodeSerializer extends ValueSerializer<Country> {
  @Override
  public void serialize(Country value, JsonGenerator generator, SerializationContext context) {
    generator.writeString(value.getIsoCode());
  }
}
