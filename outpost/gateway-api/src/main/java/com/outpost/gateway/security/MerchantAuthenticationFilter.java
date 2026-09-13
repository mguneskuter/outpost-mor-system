package com.outpost.gateway.security;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import com.outpost.framework.security.web.SizeBoundedRequestBody;
import com.outpost.gateway.api.ErrorResponse;
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
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Authenticates signed Gateway requests and exposes the account identity downstream. */
public final class MerchantAuthenticationFilter extends OncePerRequestFilter {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(MerchantAuthenticationFilter.class));
  private static final String ACTUATOR_PATH_PREFIX = "/actuator/";
  private static final String PSP_WEBHOOK_PATH_PATTERN = "/v1/psp/[^/]+/webhook";
  public static final String PRINCIPAL_ATTRIBUTE =
      MerchantAuthenticationFilter.class.getName() + ".principal";
  private final MerchantApiKeyRepository merchantApiKeys;
  private final String operatorKey;
  private final AesGcmSecretAdapter secrets;
  private final ObjectMapper objectMapper;

  /** Creates a filter backed by merchant key storage that answers rejections as JSON. */
  public MerchantAuthenticationFilter(
      MerchantApiKeyRepository merchantApiKeys,
      String operatorKey,
      AesGcmSecretAdapter secrets,
      ObjectMapper objectMapper) {
    this.merchantApiKeys = merchantApiKeys;
    this.operatorKey = operatorKey;
    this.secrets = secrets;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith(ACTUATOR_PATH_PREFIX)
        || request.getRequestURI().equals("/livez")
        || request.getRequestURI().equals("/readyz")
        || request.getRequestURI().matches(PSP_WEBHOOK_PATH_PATTERN)) {
      chain.doFilter(request, response);
      return;
    }
    String presented = request.getHeader("X-Outpost-Api-Key");
    if (presented == null || presented.isBlank()) {
      rejected("MISSING_API_KEY");
      unauthenticated(response);
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
    Optional<byte[]> boundedBody = SizeBoundedRequestBody.read(request);
    if (boundedBody.isEmpty()) {
      reject(
          response,
          HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          ErrorResponse.of(ErrorResponse.BODY_TOO_LARGE));
      return;
    }
    byte[] body = boundedBody.orElseThrow();
    // Credential failures are answered here rather than rethrown: the servlet container logs an
    // escaping exception as a second ERROR event.
    Optional<MerchantApiKeyCredentials> credentials;
    try {
      credentials = merchantApiKeys.findActiveByHash(sha256Hex(presented));
    } catch (RuntimeException exception) {
      String correlationId = UUID.randomUUID().toString();
      LOGGER.error(
          "Merchant credential lookup failed",
          exception,
          new StructuredLogField(LogField.FAILURE, "CREDENTIAL_STORE"),
          new StructuredLogField(LogField.CORRELATION_ID, correlationId));
      internalError(response, correlationId);
      return;
    }
    if (credentials.isEmpty()) {
      rejected("UNKNOWN_API_KEY");
      unauthenticated(response);
      return;
    }
    String signature = request.getHeader("X-Outpost-Signature");
    if (signature == null || signature.isBlank()) {
      rejected("MISSING_SIGNATURE");
      unauthenticated(response);
      return;
    }
    MerchantApiKeyCredentials key = credentials.orElseThrow();
    HmacKey hmacKey;
    try {
      hmacKey = HmacKey.fromUtf8(secrets.decrypt(key.encryptedHmacSecret()));
    } catch (RuntimeException exception) {
      String correlationId = UUID.randomUUID().toString();
      LOGGER.error(
          "Merchant credential decryption failed",
          exception,
          new StructuredLogField(LogField.FAILURE, "CREDENTIAL_DECRYPTION"),
          new StructuredLogField(LogField.MERCHANT_ACCOUNT_ID, Long.toString(key.accountId())),
          new StructuredLogField(LogField.CORRELATION_ID, correlationId));
      internalError(response, correlationId);
      return;
    }
    boolean validSignature;
    try {
      validSignature = HmacSha256.verify(hmacKey, body, HmacSignature.fromBase64(signature));
    } catch (IllegalArgumentException exception) {
      LOGGER.warn(
          "Merchant signature rejected",
          exception,
          new StructuredLogField(LogField.FAILURE, "INVALID_SIGNATURE"));
      unauthenticated(response);
      return;
    }
    if (!validSignature) {
      LOGGER.warn(
          "Merchant signature rejected",
          new StructuredLogField(LogField.FAILURE, "INVALID_SIGNATURE"));
      unauthenticated(response);
      return;
    }
    HttpServletRequest wrapped = new BodyRequest(request, body);
    wrapped.setAttribute(PRINCIPAL_ATTRIBUTE, GatewayPrincipal.merchant(key.accountId()));
    chain.doFilter(wrapped, response);
  }

  private static void rejected(String failure) {
    LOGGER.warn(
        "Merchant authentication rejected", new StructuredLogField(LogField.FAILURE, failure));
  }

  private void unauthenticated(HttpServletResponse response) throws IOException {
    reject(
        response,
        HttpServletResponse.SC_UNAUTHORIZED,
        ErrorResponse.of(ErrorResponse.UNAUTHENTICATED));
  }

  private void internalError(HttpServletResponse response, String correlationId)
      throws IOException {
    reject(
        response,
        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        new ErrorResponse(ErrorResponse.INTERNAL_ERROR, correlationId));
  }

  private void reject(HttpServletResponse response, int status, ErrorResponse body)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
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

  private enum LogField implements LogFields {
    FAILURE("authentication_failure"),
    MERCHANT_ACCOUNT_ID("merchant_account_id"),
    CORRELATION_ID("correlation_id");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
