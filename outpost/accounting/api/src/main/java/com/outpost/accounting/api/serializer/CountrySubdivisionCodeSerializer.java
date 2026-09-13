package com.outpost.accounting.api.serializer;

import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes a country subdivision as its ISO 3166-2 code. */
public final class CountrySubdivisionCodeSerializer extends ValueSerializer<CountrySubdivision> {
  @Override
  public void serialize(
      CountrySubdivision value, JsonGenerator generator, SerializationContext context) {
    generator.writeString(value.getCode());
  }
}
