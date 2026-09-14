package com.outpost.framework.security.hmac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.security.apikey.ApiKey;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class HmacSha256Test {

  private static final HmacKey KEY = HmacKey.fromUtf8("test-key");
  private static final byte[] PAYLOAD = "exact raw payload".getBytes(StandardCharsets.UTF_8);

  @Test
  void signsAndVerifiesExactPayload() {
    HmacSignature signature = HmacSha256.sign(KEY, PAYLOAD);

    assertThat(HmacSha256.verify(KEY, PAYLOAD, signature)).isTrue();
    assertThat(HmacSha256.verify(KEY, "changed".getBytes(StandardCharsets.UTF_8), signature))
        .isFalse();
  }

  @Test
  void rejectsAlteredAndMalformedSignatures() {
    HmacSignature signature = HmacSha256.sign(KEY, PAYLOAD);
    byte[] altered = signature.copyBytes();
    altered[0] ^= 1;

    assertThat(HmacSha256.verify(KEY, PAYLOAD, HmacSignature.of(altered))).isFalse();
    assertThatThrownBy(() -> HmacSignature.of(new byte[31]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HmacSignature.fromBase64("not-base64"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullInputsAndEmptyKeyMaterial() {
    assertThatThrownBy(() -> HmacKey.of(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    assertThat(HmacSha256.verify(null, PAYLOAD, HmacSha256.sign(KEY, PAYLOAD))).isFalse();
    assertThat(HmacSha256.verify(KEY, (byte @Nullable []) null, HmacSha256.sign(KEY, PAYLOAD)))
        .isFalse();
    assertThat(HmacSha256.verify(KEY, PAYLOAD, null)).isFalse();
  }

  @Test
  void secretMaterialIsAbsentFromDiagnostics() {
    assertThat(KEY.toString()).doesNotContain("test-key");
    assertThat(HmacSha256.sign(KEY, PAYLOAD).toString()).doesNotContain("exact raw payload");
    assertThat(ApiKey.fromUtf8("merchant-secret").toString()).doesNotContain("merchant-secret");
  }

  @Test
  void comparesApiKeysWithoutExposingTheirBytes() {
    ApiKey expected = ApiKey.fromUtf8("merchant-key");

    assertThat(expected.matches(ApiKey.fromUtf8("merchant-key"))).isTrue();
    assertThat(expected.matches(ApiKey.fromUtf8("different-key"))).isFalse();
    assertThat(expected.matches(null)).isFalse();
    assertThat(expected.toString()).isEqualTo("ApiKey[REDACTED]");
  }
}
