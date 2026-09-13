package com.outpost.merchant.cli.gateway;

/** The Gateway refused or failed a request; the code is the one its error body carries. */
public final class GatewayException extends RuntimeException {
  private final int status;
  private final String code;

  GatewayException(int status, String code) {
    super("HTTP " + status + " " + code);
    this.status = status;
    this.code = code;
  }

  /** The HTTP status the Gateway answered. */
  public int status() {
    return status;
  }

  /** The Gateway's error code, or the raw body when it carried none. */
  public String code() {
    return code;
  }

  /** The status and code as the shell prints them. */
  public String describe() {
    return "HTTP " + status + " " + code;
  }
}
