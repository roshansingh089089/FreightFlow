package com.freightflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.common.exception.ApiException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GeminiClient implements AiModelClient {
  private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
  private final String apiKey;
  private final String model;
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final Duration requestTimeout;

  @Autowired
  GeminiClient(
      @Value("${gemini.api-key:}") String apiKey,
      @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
      @Value("${gemini.request-timeout-seconds:20}") int requestTimeoutSeconds,
      ObjectMapper mapper) {
    this(
        apiKey,
        model,
        mapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        Duration.ofSeconds(requestTimeoutSeconds));
  }

  GeminiClient(String apiKey, String model, ObjectMapper mapper, HttpClient http) {
    this(apiKey, model, mapper, http, Duration.ofSeconds(20));
  }

  GeminiClient(String apiKey, String model, ObjectMapper mapper) {
    this(
        apiKey,
        model,
        mapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  GeminiClient(
      String apiKey, String model, ObjectMapper mapper, HttpClient http, Duration requestTimeout) {
    this.apiKey = apiKey;
    this.model = model;
    this.mapper = mapper;
    this.http = http;
    this.requestTimeout = requestTimeout;
  }

  boolean configured() {
    return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("replace_");
  }

  public JsonNode generate(String instruction, String input, Map<String, Object> schema) {
    if (!configured())
      throw new ApiException(
          503, "AI_NOT_CONFIGURED", "AI is not configured. You can continue manually.");
    long started = System.nanoTime();
    boolean providerStarted = false;
    try {
      Map<String, Object> config = new LinkedHashMap<>();
      config.put("temperature", 0.1);
      config.put(
          "responseFormat",
          Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema)));
      Map<String, Object> body =
          Map.of(
              "store",
              false,
              "systemInstruction",
              Map.of("parts", List.of(Map.of("text", instruction))),
              "contents",
              List.of(Map.of("parts", List.of(Map.of("text", input)))),
              "generationConfig",
              config);
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(
                      "https://generativelanguage.googleapis.com/v1beta/models/"
                          + model
                          + ":generateContent"))
              .header("Content-Type", "application/json")
              .header("x-goog-api-key", apiKey)
              .timeout(requestTimeout)
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      providerStarted = true;
      logger.debug(
          "AI_PROVIDER_START provider=Gemini model={} requestId={}",
          model,
          AiDiagnostics.requestId());
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        providerEnd("failure", response.statusCode(), "PROVIDER_HTTP_ERROR", "none", started);
        throw new ApiException(
            502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.");
      }
      providerEnd("success", response.statusCode(), "none", "none", started);
      JsonNode envelope;
      try {
        envelope = mapper.readTree(response.body());
      } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
        parseEnd("failure", "MALFORMED_ENVELOPE", malformed.getClass().getSimpleName());
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.",
            malformed);
      }
      JsonNode candidate = envelope.path("candidates").path(0);
      String text = null;
      for (JsonNode part : candidate.path("content").path("parts"))
        if (part.path("text").isTextual() && !part.path("text").asText().isBlank())
          text = part.path("text").asText();
      if (text == null) {
        IllegalStateException missing = new IllegalStateException("missing text part");
        parseEnd("failure", "MISSING_TEXT", missing.getClass().getSimpleName());
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.",
            missing);
      }
      JsonNode parsed;
      try {
        parsed = mapper.readTree(text);
      } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
        parseEnd("failure", "MALFORMED_JSON", malformed.getClass().getSimpleName());
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.",
            malformed);
      }
      if (!parsed.isObject()) {
        IllegalStateException invalid = new IllegalStateException("response is not an object");
        parseEnd("failure", "NON_OBJECT_JSON", invalid.getClass().getSimpleName());
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.",
            invalid);
      }
      parseEnd("success", "none", "none");
      return parsed;
    } catch (ApiException e) {
      throw e;
    } catch (HttpConnectTimeoutException e) {
      providerEnd("failure", null, "CONNECTION_TIMEOUT", e.getClass().getSimpleName(), started);
      throw new ApiException(
          504, "AI_PROVIDER_ERROR", "AI timed out. You can continue manually.", e);
    } catch (java.net.http.HttpTimeoutException e) {
      providerEnd("failure", null, "REQUEST_TIMEOUT", e.getClass().getSimpleName(), started);
      throw new ApiException(
          504, "AI_PROVIDER_ERROR", "AI timed out. You can continue manually.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      providerEnd("failure", null, "INTERRUPTED", e.getClass().getSimpleName(), started);
      throw new ApiException(
          502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.", e);
    } catch (java.io.IOException e) {
      providerEnd(
          "failure",
          null,
          e instanceof ConnectException ? "CONNECTION_FAILURE" : "NETWORK_IO",
          e.getClass().getSimpleName(),
          started);
      throw new ApiException(
          502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.", e);
    } catch (Exception e) {
      providerEnd(
          "failure",
          null,
          providerStarted ? "CLIENT_ERROR" : "REQUEST_BUILD",
          e.getClass().getSimpleName(),
          started);
      throw new ApiException(
          502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.", e);
    }
  }

  private void providerEnd(
      String outcome, Integer httpStatus, String category, String exceptionType, long started) {
    logger.debug(
        "AI_PROVIDER_END provider=Gemini model={} requestId={} outcome={} httpStatus={} elapsedMs={} category={} exceptionType={} classMethod=GeminiClient.generate",
        model,
        AiDiagnostics.requestId(),
        outcome,
        httpStatus == null ? "none" : httpStatus,
        (System.nanoTime() - started) / 1_000_000,
        category,
        exceptionType);
  }

  private void parseEnd(String outcome, String category, String exceptionType) {
    logger.debug(
        "AI_RESPONSE_PARSE requestId={} outcome={} category={} exceptionType={} classMethod=GeminiClient.generate",
        AiDiagnostics.requestId(),
        outcome,
        category,
        exceptionType);
  }
}
