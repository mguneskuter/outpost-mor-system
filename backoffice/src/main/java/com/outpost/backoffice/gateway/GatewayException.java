package com.outpost.backoffice.gateway;

/** The Gateway refused or failed a request; the code is the one its error body carries. */
public final class GatewayException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates the failure the Gateway answered with this status and code. */
  public GatewayException(int status, String code) {
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

  /** The status and code as the page shows them. */
  public String describe() {
    return "HTTP " + status + " " + code;
  }
}
