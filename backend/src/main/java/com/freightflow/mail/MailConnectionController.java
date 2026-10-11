package com.freightflow.mail;

import com.freightflow.common.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/connections")
class MailConnectionController {
  record SaveRequest(
      @NotBlank String providerType,
      @Email @NotBlank String emailAddress,
      @NotBlank String imapHost,
      @NotNull Integer imapPort,
      @NotBlank String imapSecurityMode,
      @NotBlank String imapUsername,
      @NotBlank String imapPassword,
      @NotBlank String smtpHost,
      @NotNull Integer smtpPort,
      @NotBlank String smtpSecurityMode,
      @NotBlank String smtpUsername,
      @NotBlank String smtpPassword,
      @NotBlank String smtpFrom) {}

  record View(
      UUID id,
      String providerType,
      String emailAddress,
      String status,
      boolean active,
      Instant lastSyncedAt,
      String imapHost,
      Integer imapPort,
      String imapSecurityMode,
      String smtpHost,
      Integer smtpPort,
      String smtpSecurityMode) {}

  record TestResult(boolean imapOk, boolean smtpOk, String message) {}

  private final MailConnectionService service;

  MailConnectionController(MailConnectionService service) {
    this.service = service;
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @PostMapping("/test")
  TestResult testCandidate(@Valid @RequestBody SaveRequest body) {
    SecurityConfig.current();
    return service.testCandidate(body);
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @PostMapping
  View save(@Valid @RequestBody SaveRequest body) {
    return service.save(SecurityConfig.current(), body);
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/current")
  ResponseEntity<View> current() {
    View result = service.current(SecurityConfig.current());
    return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @PostMapping("/{id}/test")
  TestResult test(@PathVariable UUID id) {
    return service.test(SecurityConfig.current(), id);
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @DeleteMapping("/{id}")
  ResponseEntity<Void> disconnect(@PathVariable UUID id) {
    service.disconnect(SecurityConfig.current(), id);
    return ResponseEntity.noContent().build();
  }
}
