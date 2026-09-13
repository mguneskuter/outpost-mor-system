package com.outpost.framework.security.web;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
 * SizeBoundedRequestBody} is answered {@code 413 BODY_TOO_LARGE} and goes no further.
 */
public final class SignatureAuthenticationFilter extends OncePerRequestFilter {
  /** The header that carries the Base64 HMAC-SHA-256 signature of the exact request body. */
  public static final String SIGNATURE_HEADER = "X-Outpost-Signature";

  /** The error code of a signed body larger than {@link SizeBoundedRequestBody#MAX_SIZE_BYTES}. */
  public static final String BODY_TOO_LARGE = "BODY_TOO_LARGE";

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();
  private final Map<String, HmacKey> keysByCaller;
  private final ErrorBodyWriter errorBodies;

  /**
   * Creates a filter that recognises each named caller by its key and answers a rejected request
   * through {@code errorBodies}.
   *
   * @throws IllegalArgumentException when two callers share a key, since a signature would then not
   *     identify one caller
   */
  public SignatureAuthenticationFilter(
      Map<String, HmacKey> keysByCaller, ErrorBodyWriter errorBodies) {
    this.keysByCaller = Map.copyOf(keysByCaller);
    this.errorBodies = errorBodies;
    List<HmacKey> keys = List.copyOf(this.keysByCaller.values());
    for (int i = 0; i < keys.size(); i++) {
      for (int j = i + 1; j < keys.size(); j++) {
        if (keys.get(i).equals(keys.get(j))) {
          throw new IllegalArgumentException("Callers must not share an HMAC key");
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
      errorBodies.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, BODY_TOO_LARGE);
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
    for (Map.Entry<String, HmacKey> entry : keysByCaller.entrySet()) {
      if (HmacSha256.verifyBase64(entry.getValue(), body, encodedSignature)) {
        return entry.getKey();
      }
    }
    return null;
  }
}
