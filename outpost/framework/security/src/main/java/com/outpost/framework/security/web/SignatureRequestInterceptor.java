package com.outpost.framework.security.web;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Signs outgoing requests for {@link SignatureAuthenticationFilter}: the {@code
 * X-Outpost-Signature} header carries the Base64 HMAC-SHA-256 of the exact body bytes sent, which
 * are empty for a request without a body.
 */
public final class SignatureRequestInterceptor implements ClientHttpRequestInterceptor {
  private final HmacKey signingKey;

  /** Creates an interceptor that signs with the calling service's key. */
  public SignatureRequestInterceptor(HmacKey signingKey) {
    this.signingKey = signingKey;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request
        .getHeaders()
        .set(
            SignatureAuthenticationFilter.SIGNATURE_HEADER,
            HmacSha256.sign(signingKey, body).toBase64());
    return execution.execute(request, body);
  }
}
