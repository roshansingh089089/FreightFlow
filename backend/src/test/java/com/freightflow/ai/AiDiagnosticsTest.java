package com.freightflow.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.common.exception.ApiErrors;
import com.freightflow.common.exception.ApiException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiDiagnosticsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<Logger> loggers = new ArrayList<>();
  private final List<Level> previousLevels = new ArrayList<>();
  private final ListAppender<ILoggingEvent> events = new ListAppender<>();

  @BeforeEach
  void capture() {
    events.start();
    for (Class<?> type :
        List.of(AiRequestLoggingFilter.class, AiDiagnostics.class, GeminiClient.class)) {
      Logger logger = (Logger) LoggerFactory.getLogger(type);
      loggers.add(logger);
      previousLevels.add(logger.getLevel());
      logger.setLevel(Level.DEBUG);
      logger.addAppender(events);
    }
  }

  @AfterEach
  void release() {
    for (int i = 0; i < loggers.size(); i++) {
      loggers.get(i).detachAppender(events);
      loggers.get(i).setLevel(previousLevels.get(i));
    }
    events.stop();
    MDC.clear();
  }

  private String lines() {
    return events.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }

  private MockMvc mvc(AiAssistanceService assistance) {
    return MockMvcBuilders.standaloneSetup(
            new AiController(mock(AiShipmentService.class), assistance))
        .setControllerAdvice(new ApiErrors())
        .addFilters(new AiRequestLoggingFilter())
        .build();
  }

  @Test
  void requestIdFlowsFromControllerToProviderAndIsCleared() throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    GeminiClient client = new GeminiClient("test-key", "fixture-model", mapper, http);
    AiAssistanceService assistance = mock(AiAssistanceService.class);
    when(assistance.naturalSearch(anyString()))
        .thenAnswer(
            ignored ->
                Map.of(
                    "result", client.generate("instruction", "input", Map.of("type", "object"))));

    var result =
        mvc(assistance)
            .perform(
                post("/api/ai/search")
                    .header("X-Request-ID", "safe-fixture-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"query\"}"))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("safe-fixture-123", result.getResponse().getHeader("X-Request-ID"));
    String log = lines();
    for (String checkpoint :
        List.of(
            "AI_REQUEST_START",
            "AI_VALIDATION",
            "AI_PROVIDER_START",
            "AI_PROVIDER_END",
            "AI_RESPONSE_PARSE",
            "AI_REQUEST_END")) assertTrue(log.contains(checkpoint), checkpoint);
    assertTrue(log.contains("requestId=safe-fixture-123"));
    assertTrue(log.matches("(?s).*AI_REQUEST_END.*finalStatus=200.*elapsedMs=[0-9]+.*"));
    assertTrue(log.matches("(?s).*AI_PROVIDER_END.*httpStatus=200 elapsedMs=[0-9]+.*"));
    assertFalse(log.contains("instruction"));
    assertNull(MDC.get("requestId"));
  }

  @Test
  void validationFailureHasSafeCodeAndNoProviderCall() throws Exception {
    mvc(mock(AiAssistanceService.class))
        .perform(
            post("/api/ai/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
        .andExpect(status().isBadRequest());
    String log = lines();
    assertTrue(log.contains("AI_VALIDATION"));
    assertTrue(log.contains("outcome=failure code=VALIDATION_ERROR"));
    assertTrue(log.contains("AI_REQUEST_END"));
    assertTrue(log.contains("finalStatus=400"));
    assertFalse(log.contains("AI_PROVIDER_START"));
  }

  @Test
  void timeoutAndHttpFailureHaveDifferentCategories() throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("private provider detail"));
    GeminiClient client = new GeminiClient("test-key", "fixture-model", mapper, http);
    MDC.put("requestId", "timeout-fixture");
    ApiException timeout =
        assertThrows(
            ApiException.class,
            () -> client.generate("private prompt", "private email", Map.of("type", "object")));
    assertEquals(504, timeout.status);
    assertInstanceOf(HttpTimeoutException.class, timeout.getCause());
    assertTrue(lines().contains("category=REQUEST_TIMEOUT exceptionType=HttpTimeoutException"));
    assertFalse(lines().contains("private prompt"));
    assertFalse(lines().contains("private email"));
    assertFalse(lines().contains("private provider detail"));

    reset(http);
    @SuppressWarnings("unchecked")
    HttpResponse<String> failure = mock(HttpResponse.class);
    when(failure.statusCode()).thenReturn(503);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(failure);
    ApiException rejected =
        assertThrows(
            ApiException.class,
            () -> client.generate("private prompt", "private email", Map.of("type", "object")));
    assertEquals(502, rejected.status);
    assertTrue(lines().contains("httpStatus=503"));
    assertTrue(lines().contains("category=PROVIDER_HTTP_ERROR"));
  }

  @Test
  void malformedProviderResponseIsLoggedAsParseFailure() throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("not json");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    GeminiClient client = new GeminiClient("test-key", "fixture-model", mapper, http);
    ApiException error =
        assertThrows(
            ApiException.class,
            () -> client.generate("instruction", "input", Map.of("type", "object")));
    assertEquals("AI_INVALID_RESPONSE", error.code);
    assertNotNull(error.getCause());
    assertTrue(lines().contains("AI_RESPONSE_PARSE"));
    assertTrue(lines().contains("category=MALFORMED_ENVELOPE"));
  }
}
