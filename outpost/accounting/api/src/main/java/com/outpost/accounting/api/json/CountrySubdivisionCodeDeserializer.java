package com.outpost.accounting.api.json;

import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reads a subdivision from its ISO 3166-2 code; an unknown code fails deserialization. */
public final class CountrySubdivisionCodeDeserializer
    extends ValueDeserializer<CountrySubdivision> {
  @Override
  public CountrySubdivision deserialize(JsonParser parser, DeserializationContext context) {
    String code = parser.getValueAsString();
    if (code == null) {
      return context.reportInputMismatch(this, "subdivision must be an ISO 3166-2 code");
    }
    return CountrySubdivisions.fromCode(code)
        .orElseThrow(
            () ->
                context.weirdStringException(
                    code, CountrySubdivision.class, "unknown ISO 3166-2 subdivision code"));
  }
}
