package com.freightflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.common.exception.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
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

  @Autowired
  GeminiClient(
      @Value("${gemini.api-key:}") String apiKey,
      @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
      ObjectMapper mapper) {
    this(
        apiKey,
        model,
        mapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  GeminiClient(String apiKey, String model, ObjectMapper mapper, HttpClient http) {
    this.apiKey = apiKey;
    this.model = model;
    this.mapper = mapper;
    this.http = http;
  }

  boolean configured() {
    return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("replace_");
  }

  public JsonNode generate(String instruction, String input, Map<String, Object> schema) {
    if (!configured())
      throw new ApiException(
          503, "AI_NOT_CONFIGURED", "AI is not configured. You can continue manually.");
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
              .timeout(Duration.ofSeconds(20))
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ApiException(
            502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.");
      JsonNode envelope;
      try {
        envelope = mapper.readTree(response.body());
      } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
        logger.warn("Gemini returned malformed response envelope");
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.");
      }
      JsonNode candidate = envelope.path("candidates").path(0);
      String text = null;
      for (JsonNode part : candidate.path("content").path("parts"))
        if (part.path("text").isTextual() && !part.path("text").asText().isBlank())
          text = part.path("text").asText();
      if (text == null) {
        logger.warn(
            "Gemini response has no text part; finishReason={}",
            candidate.path("finishReason").asText("unknown"));
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.");
      }
      JsonNode parsed;
      try {
        parsed = mapper.readTree(text);
      } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
        logger.warn("Gemini text part is not valid JSON");
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.");
      }
      if (!parsed.isObject()) {
        logger.warn("Gemini JSON response is not an object");
        throw new ApiException(
            502,
            "AI_INVALID_RESPONSE",
            "AI returned an invalid response. You can continue manually.");
      }
      return parsed;
    } catch (ApiException e) {
      throw e;
    } catch (java.net.http.HttpTimeoutException e) {
      throw new ApiException(504, "AI_PROVIDER_ERROR", "AI timed out. You can continue manually.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(
          502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.");
    } catch (Exception e) {
      throw new ApiException(
          502, "AI_PROVIDER_ERROR", "AI is temporarily unavailable. You can continue manually.");
    }
  }
}
