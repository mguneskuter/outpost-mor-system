package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.accounting.api.serializer.AmountDeserializer;
import com.outpost.accounting.api.serializer.AmountSerializer;
import com.outpost.accounting.api.serializer.CountryIsoCodeDeserializer;
import com.outpost.accounting.api.serializer.CountryIsoCodeSerializer;
import com.outpost.accounting.api.serializer.CountrySubdivisionCodeDeserializer;
import com.outpost.accounting.api.serializer.CountrySubdivisionCodeSerializer;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.payment.common.Amount;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/** One request to change a payment's accounting, sent by Gateway to the Ledger. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AccountingQueueRequest(
    @JsonProperty("type") AccountingQueueRequestTypes type,
    @JsonProperty("original_reference") String originalReference,
    @JsonProperty("merchant_reference") String merchantReference,
    @JsonProperty("psp_code") String pspCode,
    @JsonProperty("psp_reference") String pspReference,
    @JsonProperty("success") @Nullable Boolean success,
    @JsonProperty("refund_reference") @Nullable String refundReference,
    @JsonProperty("merchant_code") @Nullable String merchantCode,
    @JsonProperty("shopper_country")
        @JsonSerialize(using = CountryIsoCodeSerializer.class)
        @JsonDeserialize(using = CountryIsoCodeDeserializer.class)
        @Nullable Country shopperCountry,
    @JsonProperty("shopper_country_subdivision")
        @JsonSerialize(using = CountrySubdivisionCodeSerializer.class)
        @JsonDeserialize(using = CountrySubdivisionCodeDeserializer.class)
        @Nullable CountrySubdivision shopperCountrySubdivision,
    @JsonProperty("net_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount netAmount,
    @JsonProperty("tax_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount taxAmount,
    @JsonProperty("gross_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount grossAmount) {

  /**
   * Returns the fields that identify this request on a log line: its type and reference, and the
   * PSP outcome and refund reference when the request carries them.
   */
  public StructuredLogField[] logFields() {
    List<StructuredLogField> fields = new ArrayList<>();
    fields.add(new StructuredLogField(LogField.REQUEST_TYPE, type.name()));
    fields.add(new StructuredLogField(LogField.ORIGINAL_REFERENCE, originalReference));
    if (success != null) {
      fields.add(new StructuredLogField(LogField.SUCCESS, success.toString()));
    }
    if (refundReference != null) {
      fields.add(new StructuredLogField(LogField.REFUND_REFERENCE, refundReference));
    }
    return fields.toArray(StructuredLogField[]::new);
  }

  private enum LogField implements LogFields {
    REQUEST_TYPE("request_type"),
    ORIGINAL_REFERENCE("original_reference"),
    SUCCESS("success"),
    REFUND_REFERENCE("refund_reference");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
