package com.freightflow.ai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AiRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(AiRequestLoggingFilter.class);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getMethod().equals("OPTIONS") || !request.getRequestURI().startsWith("/api/ai/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String previousId = MDC.get("requestId");
    String suppliedId = request.getHeader("X-Request-ID");
    String id =
        safeId(previousId)
            ? previousId
            : safeId(suppliedId) ? suppliedId : UUID.randomUUID().toString();
    long started = System.nanoTime();
    MDC.put("requestId", id);
    request.setAttribute(AiDiagnostics.REQUEST_ID, id);
    response.setHeader("X-Request-ID", id);
    String operation = operation(request.getRequestURI());
    logger.debug(
        "AI_REQUEST_START endpoint={} operation={} requestId={}",
        endpoint(operation),
        operation,
        id);
    try {
      chain.doFilter(request, response);
    } finally {
      long elapsedMs = (System.nanoTime() - started) / 1_000_000;
      Object code = request.getAttribute(AiDiagnostics.ERROR_CODE);
      Object type = request.getAttribute(AiDiagnostics.ERROR_TYPE);
      Object location = request.getAttribute(AiDiagnostics.ERROR_LOCATION);
      logger.debug(
          "AI_REQUEST_END requestId={} finalStatus={} errorCode={} elapsedMs={} exceptionType={} classMethod={}",
          id,
          response.getStatus(),
          code == null ? "none" : code,
          elapsedMs,
          type == null ? "none" : type,
          location == null ? "none" : location);
      if (previousId == null) MDC.remove("requestId");
      else MDC.put("requestId", previousId);
    }
  }

  private static boolean safeId(String id) {
    return id != null && id.matches("[A-Za-z0-9_-]{1,64}");
  }

  private static String operation(String uri) {
    if (uri.equals("/api/ai/mail/draft")) return "MAIL_DRAFT";
    if (uri.equals("/api/ai/shipment-extract")) return "SHIPMENT_EXTRACTION";
    if (uri.equals("/api/ai/customers/extract")) return "CUSTOMER_EXTRACTION";
    if (uri.equals("/api/ai/draft-clarification")) return "CLARIFICATION_DRAFT";
    if (uri.equals("/api/ai/search")) return "AI_SEARCH";
    if (uri.matches("/api/ai/customers/[^/]+/summary")) return "CUSTOMER_SUMMARY";
    if (uri.matches("/api/ai/quotations/[^/]+/text")) return "QUOTATION_TEXT";
    if (uri.matches("/api/ai/quotations/[^/]+/check")) return "QUOTATION_CHECK";
    if (uri.matches("/api/ai/quotations/[^/]+/email")) return "QUOTATION_EMAIL";
    if (uri.matches("/api/ai/quotations/[^/]+/terms")) return "QUOTATION_TERMS";
    return "UNKNOWN_AI_ENDPOINT";
  }

  private static String endpoint(String operation) {
    return switch (operation) {
      case "MAIL_DRAFT" -> "/api/ai/mail/draft";
      case "SHIPMENT_EXTRACTION" -> "/api/ai/shipment-extract";
      case "CUSTOMER_EXTRACTION" -> "/api/ai/customers/extract";
      case "CLARIFICATION_DRAFT" -> "/api/ai/draft-clarification";
      case "AI_SEARCH" -> "/api/ai/search";
      case "CUSTOMER_SUMMARY" -> "/api/ai/customers/{id}/summary";
      case "QUOTATION_TEXT" -> "/api/ai/quotations/{id}/text";
      case "QUOTATION_CHECK" -> "/api/ai/quotations/{id}/check";
      case "QUOTATION_EMAIL" -> "/api/ai/quotations/{id}/email";
      case "QUOTATION_TERMS" -> "/api/ai/quotations/{id}/terms";
      default -> "/api/ai/unknown";
    };
  }
}
