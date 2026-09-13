package com.outpost.gateway.security;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import com.outpost.gateway.security.repository.MerchantApiKeyCredentials;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates signed Gateway requests and exposes the account identity downstream. */
public final class MerchantAuthenticationFilter extends OncePerRequestFilter {
  private static final String ACTUATOR_PATH_PREFIX = "/actuator/";
  private static final String PSP_WEBHOOK_PATH_PATTERN = "/v1/psp/[^/]+/webhook";
  public static final String PRINCIPAL_ATTRIBUTE =
      MerchantAuthenticationFilter.class.getName() + ".principal";
  private final MerchantApiKeyRepository merchantApiKeys;
  private final String operatorKey;
  private final AesGcmSecretAdapter secrets;

  /** Creates a filter backed by merchant key storage. */
  public MerchantAuthenticationFilter(
      MerchantApiKeyRepository merchantApiKeys, String operatorKey, AesGcmSecretAdapter secrets) {
    this.merchantApiKeys = merchantApiKeys;
    this.operatorKey = operatorKey;
    this.secrets = secrets;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith(ACTUATOR_PATH_PREFIX)
        || request.getRequestURI().matches(PSP_WEBHOOK_PATH_PATTERN)) {
      chain.doFilter(request, response);
      return;
    }
    String presented = request.getHeader("X-Outpost-Api-Key");
    if (presented == null || presented.isBlank()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (operatorKey != null
        && MessageDigest.isEqual(
            presented.getBytes(StandardCharsets.UTF_8),
            operatorKey.getBytes(StandardCharsets.UTF_8))) {
      request.setAttribute(PRINCIPAL_ATTRIBUTE, GatewayPrincipal.operator());
      chain.doFilter(request, response);
      return;
    }
    Optional<MerchantApiKeyCredentials> credentials =
        merchantApiKeys.findActiveByHash(sha256Hex(presented));
    if (credentials.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    String signature = request.getHeader("X-Outpost-Signature");
    if (signature == null || signature.isBlank()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    byte[] body = request.getInputStream().readAllBytes();
    MerchantApiKeyCredentials key = credentials.orElseThrow();
    HmacKey hmacKey = HmacKey.fromUtf8(secrets.decrypt(key.encryptedHmacSecret()));
    boolean validSignature;
    try {
      validSignature = HmacSha256.verify(hmacKey, body, HmacSignature.fromBase64(signature));
    } catch (IllegalArgumentException exception) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (!validSignature) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    HttpServletRequest wrapped = new BodyRequest(request, body);
    wrapped.setAttribute(PRINCIPAL_ATTRIBUTE, GatewayPrincipal.merchant(key.accountId()));
    chain.doFilter(wrapped, response);
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static final class BodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    BodyRequest(HttpServletRequest request, byte[] body) {
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
  }
}
