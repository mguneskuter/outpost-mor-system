package com.outpost.accounting.api.serializer;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reads a country from its ISO 3166-1 alpha-2 code; an unknown code fails deserialization. */
public final class CountryIsoCodeDeserializer extends ValueDeserializer<Country> {
  @Override
  public Country deserialize(JsonParser parser, DeserializationContext context) {
    String code = parser.getValueAsString();
    if (code == null) {
      return context.reportInputMismatch(this, "country must be an ISO 3166-1 alpha-2 code");
    }
    return Countries.fromIsoCode(code)
        .orElseThrow(
            () ->
                context.weirdStringException(
                    code, Country.class, "unknown ISO 3166-1 alpha-2 country code"));
  }
}
