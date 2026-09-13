package com.outpost.framework.security.web;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that authenticates a calling service from the {@code X-Outpost-Signature}
 * header: a Base64 HMAC-SHA-256 signature over the exact raw request body.
 *
 * <p>The caller whose key verifies the signature becomes the authenticated principal, with its name
 * as the only granted authority. A request without a verifying signature continues unauthenticated,
 * so the chain's authorization rules decide its outcome. A signed request's body is read in full
 * and remains readable downstream; a signed request whose body exceeds {@link
 * SizeBoundedRequestBody} is answered {@code 413} and goes no further.
 */
public final class SignatureAuthenticationFilter extends OncePerRequestFilter {
  static final String SIGNATURE_HEADER = "X-Outpost-Signature";

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();
  private final Map<String, HmacKey> keysByCaller;

  /**
   * Creates a filter that recognises each named caller by its key.
   *
   * @throws IllegalArgumentException when two callers share a key, since a signature would then not
   *     identify one caller
   */
  public SignatureAuthenticationFilter(Map<String, HmacKey> keysByCaller) {
    this.keysByCaller = Map.copyOf(keysByCaller);
    List<HmacKey> keys = List.copyOf(this.keysByCaller.values());
    for (int i = 0; i < keys.size(); i++) {
      for (int j = i + 1; j < keys.size(); j++) {
        if (keys.get(i).equals(keys.get(j))) {
          throw new IllegalArgumentException("callers must not share an HMAC key");
        }
      }
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String encodedSignature = request.getHeader(SIGNATURE_HEADER);
    if (encodedSignature == null) {
      chain.doFilter(request, response);
      return;
    }
    Optional<byte[]> boundedBody = SizeBoundedRequestBody.read(request);
    if (boundedBody.isEmpty()) {
      response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return;
    }
    byte[] body = boundedBody.orElseThrow();
    String caller = caller(body, encodedSignature);
    if (caller != null) {
      SecurityContext context = contextHolder.createEmptyContext();
      context.setAuthentication(
          UsernamePasswordAuthenticationToken.authenticated(
              caller, null, AuthorityUtils.createAuthorityList(caller)));
      contextHolder.setContext(context);
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private @Nullable String caller(byte[] body, String encodedSignature) {
    HmacSignature signature;
    try {
      signature = HmacSignature.fromBase64(encodedSignature);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
    for (Map.Entry<String, HmacKey> entry : keysByCaller.entrySet()) {
      if (HmacSha256.verify(entry.getValue(), body, signature)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = Objects.requireNonNull(body);
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
