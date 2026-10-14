package com.freightflow.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Safe metadata shared by AI request, validation, and provider logging. */
public final class AiDiagnostics {
  static final String REQUEST_ID = "ai.requestId";
  static final String ERROR_CODE = "ai.errorCode";
  static final String ERROR_TYPE = "ai.errorType";
  static final String ERROR_LOCATION = "ai.errorLocation";
  private static final Logger logger = LoggerFactory.getLogger(AiDiagnostics.class);

  private AiDiagnostics() {}

  static String requestId() {
    String id = MDC.get("requestId");
    return id == null ? "none" : id;
  }

  static void validationSuccess() {
    logger.debug("AI_VALIDATION requestId={} outcome=success", requestId());
  }

  public static void error(HttpServletRequest request, String code, Throwable cause, int status) {
    if (request == null || !request.getRequestURI().startsWith("/api/ai/")) return;
    request.setAttribute(ERROR_CODE, code);
    request.setAttribute(ERROR_TYPE, cause == null ? "none" : cause.getClass().getSimpleName());
    Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
    String location =
        handler instanceof HandlerMethod method
            ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName()
            : "ApiErrors";
    request.setAttribute(ERROR_LOCATION, location);
    if (status < 500)
      logger.debug(
          "AI_VALIDATION requestId={} outcome=failure code={} exceptionType={} classMethod={}",
          requestId(),
          code,
          cause == null ? "none" : cause.getClass().getSimpleName(),
          location);
  }
}
