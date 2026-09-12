package com.outpost.ledger.payment.api;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Authenticates the payment route over its exact raw request bytes. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SignatureFilter implements Filter {
  private final HmacKey gatewayKey;
  private final HmacKey workerKey;

  /** Creates a filter with the externally configured Gateway and Worker keys. */
  public SignatureFilter(
      @Value("${outpost.ledger.gateway-hmac-secret}") String gatewaySecret,
      @Value("${outpost.ledger.worker-hmac-secret}") String workerSecret) {
    gatewayKey = key(gatewaySecret, "Gateway");
    workerKey = key(workerSecret, "Worker");
  }

  /** Authenticates payment requests before dispatch. */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest http)
        || !(response instanceof HttpServletResponse httpResponse)
        || !"POST".equals(http.getMethod())
        || (!"/v1/payment".equals(http.getRequestURI())
            && !"/v1/payment/event".equals(http.getRequestURI())
            && !"/v1/payment/capture".equals(http.getRequestURI()))) {
      chain.doFilter(request, response);
      return;
    }
    byte[] body = http.getInputStream().readAllBytes();
    String encoded = http.getHeader("X-Outpost-Signature");
    HmacKey key = "/v1/payment".equals(http.getRequestURI()) ? gatewayKey : workerKey;
    boolean valid;
    try {
      valid = encoded != null && HmacSha256.verify(key, body, HmacSignature.fromBase64(encoded));
    } catch (IllegalArgumentException e) {
      valid = false;
    }
    if (!valid) {
      httpResponse.setStatus(401);
      httpResponse.setContentType("application/json");
      httpResponse.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
      return;
    }
    chain.doFilter(new CachedBodyRequest(http, body), response);
  }

  private static HmacKey key(String secret, String name) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(name + " HMAC key is required");
    }
    try {
      return HmacKey.fromUtf8(secret);
    } catch (RuntimeException e) {
      throw new IllegalStateException(name + " HMAC key is invalid", e);
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
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener l) {}
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
