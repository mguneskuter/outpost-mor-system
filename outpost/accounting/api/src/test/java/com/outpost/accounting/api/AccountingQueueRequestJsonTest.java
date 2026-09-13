package com.outpost.accounting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

class AccountingQueueRequestJsonTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void writesAndReadsAnOrderCreatedRequest() {
    AccountingQueueRequest request =
        new AccountingQueueRequest(
            AccountingQueueRequestTypes.ORDER_CREATED,
            "order-1",
            "merchant-order-1",
            "DEMO_PSP",
            "41",
            null,
            null,
            "DEMO_MERCHANT",
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_CA.getValue(),
            usd(10_000),
            usd(725),
            usd(10_725));

    String json = mapper.writeValueAsString(request);

    assertThat(json)
        .contains("\"shopper_country\":\"US\"")
        .contains("\"shopper_country_subdivision\":\"US-CA\"")
        .contains("\"net_amount\":{\"quantity\":10000,\"currency\":\"USD\"}");
    assertThat(mapper.readValue(json, AccountingQueueRequest.class)).isEqualTo(request);
  }

  @Test
  void writesAndReadsCaptureRequestWithAbsentOrderFields() {
    AccountingQueueRequest request =
        new AccountingQueueRequest(
            AccountingQueueRequestTypes.CAPTURE,
            "order-1",
            "merchant-order-1",
            "DEMO_PSP",
            "41",
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String json = mapper.writeValueAsString(request);

    assertThat(json)
        .contains("\"shopper_country\":null")
        .contains("\"shopper_country_subdivision\":null")
        .contains("\"net_amount\":null");
    AccountingQueueRequest read = mapper.readValue(json, AccountingQueueRequest.class);
    assertThat(read).isEqualTo(request);
    assertThat(read.shopperCountry()).isNull();
    assertThat(read.netAmount()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"shopper_country\":\"XX\"",
        "\"shopper_country_subdivision\":\"US-XX\"",
        "\"net_amount\":{\"quantity\":1,\"currency\":\"XXX\"}",
        "\"net_amount\":{\"quantity\":\"1\",\"currency\":\"USD\"}",
        "\"net_amount\":1"
      })
  void rejectsAnUnknownCodeOrMalformedAmount(String field) {
    String json = "{\"type\":\"ORDER_CREATED\",\"original_reference\":\"order-1\"," + field + "}";

    assertThatThrownBy(() -> mapper.readValue(json, AccountingQueueRequest.class))
        .isInstanceOf(DatabindException.class);
  }

  private static Amount usd(long quantity) {
    return new Amount(Currencies.USD.getValue(), quantity);
  }
}
