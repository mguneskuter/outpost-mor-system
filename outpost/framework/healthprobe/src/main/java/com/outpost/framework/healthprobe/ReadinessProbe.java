package com.outpost.framework.healthprobe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Exits {@code 0} when the readiness URL given as the only argument answers {@code 200}, {@code 1}
 * otherwise.
 *
 * <p>Service images have no shell and no {@code wget} or {@code curl}, so the container runtime's
 * healthcheck execs this class with the image's own JRE instead of a shell command.
 */
public final class ReadinessProbe {
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private ReadinessProbe() {}

  /** Requests the readiness URL in {@code args[0]} and exits with the resulting status. */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("usage: ReadinessProbe <readiness-url>");
      System.exit(1);
      return;
    }
    System.exit(isReady(args[0]) ? 0 : 1);
  }

  private static boolean isReady(String readinessUrl) {
    HttpRequest request;
    try {
      request = HttpRequest.newBuilder(URI.create(readinessUrl)).timeout(TIMEOUT).GET().build();
    } catch (IllegalArgumentException exception) {
      System.err.println("invalid readiness URL");
      return false;
    }
    HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (IOException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
