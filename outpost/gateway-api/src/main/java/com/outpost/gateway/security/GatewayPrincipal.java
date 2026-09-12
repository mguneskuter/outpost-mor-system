package com.outpost.gateway.security;

/**
 * An authenticated Gateway identity; the operator is the configured platform API key, resolved as a
 * non-merchant identity for operator-scoped reports.
 */
public record GatewayPrincipal(Type type, long accountId) {
  /** Principal categories recognized by Gateway. */
  public enum Type {
    MERCHANT,
    OPERATOR
  }

  /** Creates a merchant principal. */
  public static GatewayPrincipal merchant(long accountId) {
    return new GatewayPrincipal(Type.MERCHANT, accountId);
  }

  /** Creates the operator principal. */
  public static GatewayPrincipal operator() {
    return new GatewayPrincipal(Type.OPERATOR, 0);
  }
}
