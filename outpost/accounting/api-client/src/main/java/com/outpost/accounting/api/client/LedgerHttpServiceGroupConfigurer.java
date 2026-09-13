package com.outpost.accounting.api.client;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.web.SignatureRequestInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Points the Ledger proxies at one Ledger address as one calling service: every request is signed
 * with that caller's key and bounded by the connect and read timeouts.
 */
public final class LedgerHttpServiceGroupConfigurer
    implements RestClientHttpServiceGroupConfigurer {
  private final String baseUrl;
  private final HmacKey signingKey;
  private final Duration connectTimeout;
  private final Duration readTimeout;

  /** Creates a configurer for the caller that owns {@code signingKey}. */
  public LedgerHttpServiceGroupConfigurer(
      String baseUrl, HmacKey signingKey, Duration connectTimeout, Duration readTimeout) {
    this.baseUrl = baseUrl;
    this.signingKey = signingKey;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
  }

  @Override
  public void configureGroups(Groups<RestClient.Builder> groups) {
    groups
        .filterByName(LedgerClientConfiguration.GROUP)
        .forEachClient(
            (group, builder) ->
                builder
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory())
                    .requestInterceptor(new SignatureRequestInterceptor(signingKey)));
  }

  private JdkClientHttpRequestFactory requestFactory() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(readTimeout);
    return factory;
  }
}
