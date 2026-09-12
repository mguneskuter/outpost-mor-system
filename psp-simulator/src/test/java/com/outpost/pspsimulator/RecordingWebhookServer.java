package com.outpost.pspsimulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** A local HTTP server that records the webhooks the simulator delivers. */
public final class RecordingWebhookServer implements AutoCloseable {

  private final HttpServer server;
  private final BlockingQueue<WebhookDelivery> deliveries = new LinkedBlockingQueue<>();

  /** Starts a recording server on an available local port. */
  public RecordingWebhookServer() {
    try {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/", this::handle);
      server.start();
    } catch (IOException exception) {
      throw new UncheckedIOException("could not start the webhook server", exception);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] body = exchange.getRequestBody().readAllBytes();
    deliveries.add(
        new WebhookDelivery(
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("X-Outpost-Api-Key"),
            exchange.getRequestHeaders().getFirst("X-Outpost-Signature"),
            body));
    exchange.sendResponseHeaders(200, -1);
    exchange.close();
  }

  /** Returns the port the server listens on. */
  public int port() {
    return server.getAddress().getPort();
  }

  /** Waits for the next webhook and returns it, or null when none arrives within the timeout. */
  @Nullable
  public WebhookDelivery awaitDelivery(Duration timeout) throws InterruptedException {
    return deliveries.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** A recorded webhook delivery: the route, the authentication headers, and the raw body. */
  public record WebhookDelivery(
      String path, @Nullable String apiKey, @Nullable String signature, byte[] body) {

    /** Returns the raw body as UTF-8 text. */
    public String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
