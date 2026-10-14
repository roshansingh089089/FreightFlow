package com.freightflow.common.exception;

import com.freightflow.ai.AiDiagnostics;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(HttpServletRequest request) {
    AiDiagnostics.error(request, "FORBIDDEN", null, 403);
    return ResponseEntity.status(403)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                403,
                "code",
                "FORBIDDEN",
                "message",
                "You do not have permission for this action",
                "fieldErrors",
                Map.of()));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<?> unauthenticated(HttpServletRequest request) {
    AiDiagnostics.error(request, "UNAUTHORIZED", null, 401);
    return ResponseEntity.status(401)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                401,
                "code",
                "UNAUTHORIZED",
                "message",
                "Please log in",
                "fieldErrors",
                Map.of()));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e, HttpServletRequest request) {
    AiDiagnostics.error(request, e.code, e, e.status);
    return ResponseEntity.status(e.status)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                e.status,
                "code",
                e.code,
                "message",
                e.getMessage(),
                "fieldErrors",
                Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
    AiDiagnostics.error(request, "VALIDATION_ERROR", e, 400);
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                400,
                "code",
                "VALIDATION_ERROR",
                "message",
                "Please correct the highlighted fields",
                "fieldErrors",
                fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(HttpServletRequest request) {
    AiDiagnostics.error(request, "VALIDATION_ERROR", null, 400);
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                400,
                "code",
                "VALIDATION_ERROR",
                "message",
                "Invalid request body or parameter",
                "fieldErrors",
                Map.of()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(HttpServletRequest request) {
    AiDiagnostics.error(request, "CONFLICT", null, 409);
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                409,
                "code",
                "CONFLICT",
                "message",
                "This record conflicts with existing data",
                "fieldErrors",
                Map.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e, HttpServletRequest request) {
    AiDiagnostics.error(request, "SERVER_ERROR", e, 500);
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                500,
                "code",
                "SERVER_ERROR",
                "message",
                "An unexpected error occurred",
                "fieldErrors",
                Map.of()));
  }
}
