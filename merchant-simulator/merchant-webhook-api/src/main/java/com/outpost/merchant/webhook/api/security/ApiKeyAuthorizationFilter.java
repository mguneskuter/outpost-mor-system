package com.outpost.merchant.webhook.api.security;

import com.outpost.merchant.webhook.api.IncomingWebhookController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authorizes webhook requests with the configured API key. */
public final class ApiKeyAuthorizationFilter extends OncePerRequestFilter {
  private final String apiKey;

  /** Creates an authorization filter. */
  public ApiKeyAuthorizationFilter(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !IncomingWebhookController.WEBHOOK_EVENTS_PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!apiKey.equals(request.getHeader("X-API-Key"))) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }
}
