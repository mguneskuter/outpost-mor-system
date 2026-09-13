package com.outpost.merchant.cli.psp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;

/**
 * Pays an order the way a shopper does on the PSP simulator's payment page: a card number posted to
 * the order's payment link. The outcome reaches Outpost later by webhook.
 */
public final class PspPaymentClient {
  private final String pspApiKey;
  private final HttpClient http;
  private final ObjectMapper json;

  /** Creates a client that presents the simulator's API key on every payment. */
  public PspPaymentClient(String pspApiKey, HttpClient http, ObjectMapper json) {
    this.pspApiKey = pspApiKey;
    this.http = http;
    this.json = json;
  }

  /**
   * Submits the card for the payment the PSP knows by {@code pspReference}.
   *
   * @throws PspPaymentException when the PSP does not accept the submission
   */
  public void pay(String paymentLink, String pspReference, String cardNumber) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(paymentLink))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-Outpost-Api-Key", pspApiKey)
            .POST(
                BodyPublishers.ofByteArray(
                    json.writeValueAsBytes(new Payment(pspReference, cardNumber))))
            .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new PspPaymentException("the PSP could not be reached");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new PspPaymentException("interrupted while paying");
    }
    if (response.statusCode() != 202) {
      throw new PspPaymentException("HTTP " + response.statusCode() + " " + response.body());
    }
  }

  private record Payment(
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("card_number") String cardNumber) {}
}
