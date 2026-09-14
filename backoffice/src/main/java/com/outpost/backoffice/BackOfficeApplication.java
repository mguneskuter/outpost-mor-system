package com.outpost.backoffice;

import com.outpost.backoffice.configuration.BackOfficeProperties;
import com.outpost.backoffice.gateway.GatewayClient;
import com.outpost.backoffice.merchant.JdbcMerchantRepository;
import com.outpost.backoffice.merchant.MerchantRepository;
import com.outpost.backoffice.payment.JdbcPaymentRepository;
import com.outpost.backoffice.payment.PaymentRepository;
import com.outpost.backoffice.psp.PspPaymentClient;
import com.outpost.backoffice.register.JdbcRegisterBalanceRepository;
import com.outpost.backoffice.register.RegisterBalanceRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * A browser view of the local platform: create and pay orders as a merchant, list every payment
 * with what the Ledger booked for it, read the register balances, and read the balance reports.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackOfficeApplication {
  private static final Logger LOGGER = LoggerFactory.getLogger(BackOfficeApplication.class);

  /** Starts the back office on its port. */
  public static void main(String[] args) {
    SpringApplication.run(BackOfficeApplication.class, args);
  }

  /** Tells the operator where the back office answers once its server is up. */
  @Bean
  ApplicationListener<WebServerInitializedEvent> readyMessage() {
    return event ->
        LOGGER.info("Backoffice ready at http://localhost:{}", event.getWebServer().getPort());
  }

  @Bean
  HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Bean
  GatewayClient gatewayClient(
      BackOfficeProperties properties, HttpClient httpClient, ObjectMapper json) {
    return new GatewayClient(properties.gatewayBaseUrl(), httpClient, json);
  }

  @Bean
  PspPaymentClient pspPaymentClient(
      BackOfficeProperties properties, HttpClient httpClient, ObjectMapper json) {
    return new PspPaymentClient(properties.pspApiKey(), properties.pspBaseUrl(), httpClient, json);
  }

  @Bean
  MerchantRepository merchantRepository(JdbcClient jdbcClient) {
    return new JdbcMerchantRepository(jdbcClient);
  }

  @Bean
  PaymentRepository paymentRepository(JdbcClient jdbcClient) {
    return new JdbcPaymentRepository(jdbcClient);
  }

  @Bean
  RegisterBalanceRepository registerBalanceRepository(JdbcClient jdbcClient) {
    return new JdbcRegisterBalanceRepository(jdbcClient);
  }
}
