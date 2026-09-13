package com.outpost.framework.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class SignatureRequestInterceptorTest {
  private static final HmacKey KEY = HmacKey.fromUtf8("caller-secret");

  @Test
  void signsTheExactBodySent() throws Exception {
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("/x"));
    byte[] body = "{\"amount\":1250}".getBytes(StandardCharsets.UTF_8);

    new SignatureRequestInterceptor(KEY)
        .intercept(request, body, (sent, sentBody) -> new MockClientHttpResponse());

    String header = Objects.requireNonNull(request.getHeaders().getFirst("X-Outpost-Signature"));
    assertThat(HmacSha256.verify(KEY, body, HmacSignature.fromBase64(header))).isTrue();
  }
}
