package com.outpost.backoffice.readiness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Exits {@code 0} when the back office serves its payments page on its application port, {@code 1}
 * otherwise.
 *
 * <p>The container image has no shell and no {@code wget} or {@code curl}, so the container
 * runtime's healthcheck execs this class with the image's own JRE instead of a shell command.
 */
public final class ReadinessProbe {
  private ReadinessProbe() {}

  /** Requests {@code /payments} and exits with the resulting status. */
  public static void main(String[] args) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:8090/payments"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    try {
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      System.exit(response.statusCode() == 200 ? 0 : 1);
    } catch (IOException exception) {
      System.exit(1);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      System.exit(1);
    }
  }
}
