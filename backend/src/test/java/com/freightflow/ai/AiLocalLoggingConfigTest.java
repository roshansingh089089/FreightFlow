package com.freightflow.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = AiLocalLoggingConfigTest.MinimalApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class AiLocalLoggingConfigTest {
  @SpringBootConfiguration
  static class MinimalApp {}

  @Test
  void localAiEventCreatesDedicatedFile() throws IOException {
    MDC.put("requestId", "local-log-fixture");
    try {
      AiDiagnostics.validationSuccess();
    } finally {
      MDC.remove("requestId");
    }
    Path logFile = Path.of("logs/ai-debug.log");
    assertTrue(Files.exists(logFile));
    assertTrue(Files.readString(logFile).contains("AI_VALIDATION requestId=local-log-fixture"));
  }
}
