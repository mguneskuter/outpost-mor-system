package com.outpost.merchant.cli.command;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/** A Gateway and PSP stand-in that records every request and answers what the test scripted. */
final class StubGateway implements AutoCloseable {
  record Received(
      String method,
      String path,
      @Nullable String apiKey,
      @Nullable String signature,
      String body) {}

  private final HttpServer server;
  private final List<Received> received = new ArrayList<>();
  private final Map<String, Answer> answers;

  record Answer(int status, String body) {}

  StubGateway(Map<String, Answer> answersByMethodAndPath) throws IOException {
    this.answers = answersByMethodAndPath;
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
  }

  URI baseUrl() {
    return URI.create(
        "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
  }

  List<Received> received() {
    return List.copyOf(received);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    received.add(
        new Received(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("X-Outpost-Api-Key"),
            exchange.getRequestHeaders().getFirst("X-Outpost-Signature"),
            body));
    Answer answer =
        answers.getOrDefault(
            exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
            new Answer(404, "{\"code\":\"NOT_STUBBED\"}"));
    byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
