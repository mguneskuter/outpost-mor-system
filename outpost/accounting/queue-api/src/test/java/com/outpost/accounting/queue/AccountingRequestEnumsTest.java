package com.outpost.accounting.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AccountingRequestEnumsTest {

  @Test
  void exposesStableTypesStatusesAndResults() {
    assertThat(Arrays.stream(AccountingRequestTypes.values()).map(AccountingRequestEnumsTest::type))
        .containsExactly(
            "1:AUTHORISATION_RESULT",
            "2:CAPTURE_RESULT",
            "3:CANCELLATION_RESULT",
            "4:REFUND_RESULT",
            "5:REFUND_REQUEST");
    assertThat(
            Arrays.stream(AccountingRequestStatuses.values())
                .map(AccountingRequestEnumsTest::status))
        .containsExactly("1:RECEIVED", "2:IN_PROGRESS", "3:DONE");
    assertThat(
            Arrays.stream(AccountingRequestResults.values())
                .map(AccountingRequestEnumsTest::result))
        .containsExactly("1:SUCCESS", "2:FAILED");
  }

  private static String type(AccountingRequestTypes value) {
    return value.getValue().accountingRequestTypeId() + ":" + value.getValue().code();
  }

  private static String status(AccountingRequestStatuses value) {
    return value.getValue().accountingRequestStatusId() + ":" + value.getValue().code();
  }

  private static String result(AccountingRequestResults value) {
    return value.getValue().accountingRequestResultId() + ":" + value.getValue().code();
  }
}
