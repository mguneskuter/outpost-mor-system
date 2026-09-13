package com.outpost.merchant.webhook.api.security;

import com.outpost.merchant.webhook.api.IncomingWebhookController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verifies webhook request signatures before dispatch. */
public final class HmacSignatureVerificationFilter extends OncePerRequestFilter {
  private final HmacSignatureVerifier verifier;

  /** Creates a signature filter. */
  public HmacSignatureVerificationFilter(HmacSignatureVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !IncomingWebhookController.WEBHOOK_EVENTS_PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    byte[] body = request.getInputStream().readAllBytes();
    if (!verifier.isValid(request.getHeader("X-Outpost-Signature"), body)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      return new ServletInputStream() {
        private int index;

        @Override
        public int read() {
          return index < body.length ? body[index++] & 0xff : -1;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          if (index >= body.length) {
            return -1;
          }
          int count = Math.min(length, body.length - index);
          System.arraycopy(body, index, target, offset, count);
          index += count;
          return count;
        }

        @Override
        public boolean isFinished() {
          return index >= body.length;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener listener) {}
      };
    }

    @Override
    public java.io.BufferedReader getReader() {
      return new java.io.BufferedReader(
          new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
