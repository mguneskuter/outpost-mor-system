package com.outpost.gateway.psp.api;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.web.CachedBodyRequest;
import com.outpost.framework.security.web.SignatureAuthenticationFilter;
import com.outpost.framework.security.web.SizeBoundedRequestBody;
import com.outpost.gateway.psp.service.PspWebhookResults;
import com.outpost.integration.psp.simulator.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates PSP event notifications over their exact raw request bytes.
 *
 * <p>The signature covers the body as sent, so it is verified before the body is deserialised.
 */
public final class PspWebhookSignatureFilter extends OncePerRequestFilter {
  static final String SIGNED_REQUEST_ATTRIBUTE =
      "com.outpost.gateway.psp.api.PspWebhookSignatureFilter.signedRequest";
  private final PspConfigurationRepository configurations;
  private final ObjectMapper objectMapper;

  /** Creates a filter that resolves each addressed PSP's signing secret. */
  public PspWebhookSignatureFilter(
      PspConfigurationRepository configurations, ObjectMapper objectMapper) {
    this.configurations = configurations;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod())
        || !PspWebhookController.WEBHOOK_PATH_PATTERN.matcher(request.getRequestURI()).matches();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Matcher path = PspWebhookController.WEBHOOK_PATH_PATTERN.matcher(request.getRequestURI());
    if (!path.matches()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<byte[]> boundedBody = SizeBoundedRequestBody.read(request);
    if (boundedBody.isEmpty()) {
      response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return;
    }
    byte[] body = boundedBody.orElseThrow();
    Optional<PspConfiguration> configuration =
        configurations.findPspConfigurationByPspCode(path.group(1));
    if (configuration.isEmpty()) {
      reject(response, PspWebhookResults.UNKNOWN_PSP);
      return;
    }
    PspConfiguration psp = configuration.orElseThrow();
    if (!HmacSha256.verifyBase64(
        HmacKey.fromUtf8(psp.hmacSecret()),
        body,
        request.getHeader(SignatureAuthenticationFilter.SIGNATURE_HEADER))) {
      reject(response, PspWebhookResults.INVALID_SIGNATURE);
      return;
    }
    HttpServletRequest signed = new CachedBodyRequest(request, body);
    signed.setAttribute(SIGNED_REQUEST_ATTRIBUTE, new SignedPspWebhookRequest(psp));
    chain.doFilter(signed, response);
  }

  private void reject(HttpServletResponse response, PspWebhookResults code) throws IOException {
    ResponseEntity<PspWebhookEventResponse> rejection = PspWebhookResponder.respond(code);
    response.setStatus(rejection.getStatusCode().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), rejection.getBody());
  }
}
