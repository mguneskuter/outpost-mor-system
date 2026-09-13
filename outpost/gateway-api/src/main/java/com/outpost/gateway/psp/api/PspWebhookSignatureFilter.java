package com.outpost.gateway.psp.api;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
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
  static final String VERIFIED_WEBHOOK_ATTRIBUTE =
      "com.outpost.gateway.psp.api.PspWebhookSignatureFilter.verifiedWebhook";
  private static final Pattern WEBHOOK_PATH = Pattern.compile("/v1/psp/([^/]+)/webhook");
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
        || !WEBHOOK_PATH.matcher(request.getRequestURI()).matches();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Matcher path = WEBHOOK_PATH.matcher(request.getRequestURI());
    if (!path.matches()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<PspConfiguration> configuration = configurations.findByCode(path.group(1));
    if (configuration.isEmpty()) {
      reject(response, PspWebhookProcessResultCodes.UNKNOWN_PSP);
      return;
    }
    PspConfiguration psp = configuration.orElseThrow();
    byte[] body = request.getInputStream().readAllBytes();
    if (!hasValidSignature(psp, request.getHeader("X-Outpost-Signature"), body)) {
      reject(response, PspWebhookProcessResultCodes.INVALID_SIGNATURE);
      return;
    }
    HttpServletRequest verified = new CachedBodyRequest(request, body);
    verified.setAttribute(
        VERIFIED_WEBHOOK_ATTRIBUTE,
        new VerifiedPspWebhook(psp, new String(body, StandardCharsets.UTF_8)));
    chain.doFilter(verified, response);
  }

  private void reject(HttpServletResponse response, PspWebhookProcessResultCodes code)
      throws IOException {
    ResponseEntity<PspWebhookEventResponse> rejection = PspWebhookResponses.respond(code);
    response.setStatus(rejection.getStatusCode().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), rejection.getBody());
  }

  private static boolean hasValidSignature(
      PspConfiguration psp, @Nullable String signature, byte[] body) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    try {
      return HmacSha256.verify(
          HmacKey.fromUtf8(psp.hmacSecret()), body, HmacSignature.fromBase64(signature));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
          return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {}
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
