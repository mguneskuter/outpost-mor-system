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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Authenticates Ledger requests over their exact raw request bytes. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(LedgerAuthenticationProperties.class)
public final class SignatureFilter implements Filter {
  private final HmacKey gatewayKey;
  private final HmacKey workerKey;

  /** Creates a filter with the externally configured Gateway and Worker keys. */
  public SignatureFilter(LedgerAuthenticationProperties properties) {
    gatewayKey = key(properties.gatewayHmacSecret(), "Gateway");
    workerKey = key(properties.workerHmacSecret(), "Worker");
  }

  /** Authenticates payment requests before dispatch. */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest http)
        || !(response instanceof HttpServletResponse httpResponse)
        || (!gatewayRoute(http) && !workerRoute(http))) {
      chain.doFilter(request, response);
      return;
    }
    byte[] body = http.getInputStream().readAllBytes();
    String encoded = http.getHeader("X-Outpost-Signature");
    HmacKey key = workerRoute(http) ? workerKey : gatewayKey;
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

  private static boolean gatewayRoute(HttpServletRequest request) {
    return ("POST".equals(request.getMethod()) && "/v1/payment".equals(request.getRequestURI()))
        || ("GET".equals(request.getMethod())
            && ("/v1/report/balance/tax".equals(request.getRequestURI())
                || "/v1/report/balance/merchant".equals(request.getRequestURI())));
  }

  private static boolean workerRoute(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && ("/v1/payment/event".equals(request.getRequestURI())
            || "/v1/payment/capture".equals(request.getRequestURI())
            || "/v1/payment/refund".equals(request.getRequestURI()));
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
