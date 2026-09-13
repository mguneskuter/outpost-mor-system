package com.outpost.merchant.cli;

import com.outpost.merchant.cli.command.GuidedShell;
import com.outpost.merchant.cli.command.MerchantCommands;
import com.outpost.merchant.cli.command.OrderCommands;
import com.outpost.merchant.cli.command.ShellSession;
import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.gateway.GatewayClient;
import com.outpost.merchant.cli.merchant.JdbcMerchantRepository;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.psp.PspPaymentClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.annotation.EnableCommand;
import tools.jackson.databind.ObjectMapper;

/**
 * A merchant's shell for driving Outpost end to end: pick a merchant and PSP, create an order, pay
 * it at the PSP, refund it, and read balances.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCommand({MerchantCommands.class, OrderCommands.class})
public class MerchantCliApplication {
  /** Covers the simulator's authorisation and capture delays and the Gateway's queue polling. */
  private static final Duration OUTCOME_WAIT = Duration.ofSeconds(30);

  /**
   * Starts the shell: the guided, step-by-step session when no arguments are given, otherwise the
   * arguments run as one command and the shell exits.
   */
  public static void main(String[] args) throws Exception {
    try (ConfigurableApplicationContext context =
        SpringApplication.run(MerchantCliApplication.class, args)) {
      PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
      if (args.length == 0) {
        new GuidedShell(
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                out,
                context.getBean(MerchantRepository.class),
                context.getBean(ShellSession.class),
                context.getBean(MerchantCliProperties.class),
                context.getBean(MerchantCommands.class),
                context.getBean(OrderCommands.class))
            .run();
        return;
      }
      new NonInteractiveShellRunner(
              context.getBean(CommandParser.class), context.getBean(CommandRegistry.class), out)
          .run(args);
    }
  }

  @Bean
  HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Bean
  GatewayClient gatewayClient(
      MerchantCliProperties properties, HttpClient httpClient, ObjectMapper json) {
    return new GatewayClient(properties.gatewayBaseUrl(), httpClient, json);
  }

  @Bean
  PspPaymentClient pspPaymentClient(
      MerchantCliProperties properties, HttpClient httpClient, ObjectMapper json) {
    return new PspPaymentClient(properties.pspApiKey(), httpClient, json);
  }

  @Bean
  MerchantRepository merchantRepository(JdbcClient jdbcClient) {
    return new JdbcMerchantRepository(jdbcClient);
  }

  @Bean
  ShellSession shellSession(MerchantCliProperties properties) {
    return new ShellSession(properties.merchants());
  }

  @Bean
  MerchantCommands merchantCommands(
      MerchantRepository merchants,
      GatewayClient gateway,
      ShellSession session,
      MerchantCliProperties properties) {
    return new MerchantCommands(merchants, gateway, session, properties);
  }

  @Bean
  OrderCommands orderCommands(
      MerchantCliProperties properties,
      GatewayClient gateway,
      PspPaymentClient psp,
      MerchantRepository merchants,
      ShellSession session) {
    return new OrderCommands(properties, gateway, psp, merchants, session, OUTCOME_WAIT);
  }
}
