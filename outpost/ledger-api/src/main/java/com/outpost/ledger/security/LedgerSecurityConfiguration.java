package com.outpost.ledger.security;

import com.outpost.accounting.api.LedgerErrorResponse;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.web.ErrorBodyWriter;
import com.outpost.framework.security.web.SignatureAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Ledger's request security. On the service port every route is denied unless it is public or
 * granted to the calling service; routes are matched on the path Spring MVC dispatches, so encoded
 * and differently cased variants of a route cannot bypass its rule. On a separate management server
 * the actuator endpoints are served without a signature and every other path is denied.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LedgerSecurityConfiguration {

  /** Paths served without a signature. */
  static final List<String> PUBLIC_PATHS = List.of("/livez", "/readyz");

  private static final String GATEWAY = "GATEWAY";

  @Bean
  @Order(1)
  SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher(LedgerSecurityConfiguration::isManagementServerRequest)
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(EndpointRequest.toAnyEndpoint())
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain ledgerSecurityFilterChain(
      HttpSecurity http, LedgerAuthenticationProperties properties, ObjectMapper objectMapper)
      throws Exception {
    ErrorBodyWriter errorBodies =
        (response, status, code) -> writeError(objectMapper, response, status, code);
    return http.csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new SignatureAuthenticationFilter(
                Map.of(GATEWAY, HmacKey.fromUtf8(properties.gatewayHmacSecret())), errorBodies),
            AnonymousAuthenticationFilter.class)
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            errorBodies.write(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                LedgerErrorResponse.UNAUTHENTICATED))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            errorBodies.write(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                LedgerErrorResponse.FORBIDDEN)))
        .authorizeHttpRequests(
            requests ->
                requests
                    // An error dispatch renders the status of a request that was already handled,
                    // and the caller's authentication does not carry into it; denying it would
                    // replace that status with 401.
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS.toArray(String[]::new))
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/accounting-request")
                    .hasAuthority(GATEWAY)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/v1/report/balance/tax",
                        "/v1/report/balance/merchant",
                        "/v1/report/balance/merchant/{merchantCode}")
                    .hasAuthority(GATEWAY)
                    .anyRequest()
                    .denyAll())
        .build();
  }

  /**
   * Whether the request arrived on the management server's own web server. A management server that
   * shares the service port has no such server, so its requests stay under the service rules.
   */
  private static boolean isManagementServerRequest(HttpServletRequest request) {
    WebApplicationContext context =
        WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
    return context != null
        && WebServerApplicationContext.hasServerNamespace(
            context, WebServerNamespace.MANAGEMENT.getValue());
  }

  private static void writeError(
      ObjectMapper objectMapper, HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), LedgerErrorResponse.of(code));
  }
}
