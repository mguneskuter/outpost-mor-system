package com.outpost.framework.logging;

/** The structured log fields Outpost writes; one key has one meaning in every service. */
public enum LogFields implements LogField {
  ATTEMPTS("attempts"),
  CAPTURE_REFERENCE("capture_reference"),
  CODE("code"),
  CORRELATION_ID("correlation_id"),
  CURRENCY("currency"),
  ERROR("error"),
  EVENT("event"),
  EVENT_CODE("event_code"),
  FAILURE("authentication_failure"),
  FEE_AMOUNT("fee_amount"),
  GROSS_AMOUNT("gross_amount"),
  MERCHANT_ACCOUNT_ID("merchant_account_id"),
  MERCHANT_CODE("merchant_code"),
  NET_AMOUNT("net_amount"),
  ORDER_REFERENCE("order_reference"),
  ORIGINAL_REFERENCE("original_reference"),
  PSP_CODE("psp_code"),
  PSP_REFERENCE("psp_reference"),
  PSP_RESULT("psp_result"),
  QUEUE("queue"),
  REFUND_REFERENCE("refund_reference"),
  REJECTION_REASON("rejection_reason"),
  REQUEST_TYPE("request_type"),
  RESULT_CODE("result_code"),
  STATUS("status"),
  SUCCESS("success"),
  TAX_AMOUNT("tax_amount"),
  WEBHOOK_RESULT("webhook_result");

  private final String jsonKey;

  LogFields(String jsonKey) {
    this.jsonKey = jsonKey;
  }

  @Override
  public String getJsonKey() {
    return jsonKey;
  }
}
