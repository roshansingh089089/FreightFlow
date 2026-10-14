package com.freightflow.mail;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/stored")
class StoredMailController {
  private final OutboundMailService service;
  private final JdbcTemplate jdbc;

  StoredMailController(OutboundMailService service, JdbcTemplate jdbc) {
    this.service = service;
    this.jdbc = jdbc;
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}/sent-copy")
  Map<String, String> sentCopy(@PathVariable UUID id) {
    service.get(id); // Ownership check precedes the status lookup.
    var rows =
        jdbc.query(
            "select status,error_code from mail_sent_copies where message_id=? and tenant_id=?",
            (rs, row) ->
                Map.of(
                    "status",
                    rs.getString("status"),
                    "errorCode",
                    rs.getString("error_code") == null ? "" : rs.getString("error_code")),
            id,
            com.freightflow.common.security.SecurityConfig.current().getTenantId());
    return rows.isEmpty() ? Map.of("status", "NOT_PREPARED", "errorCode", "") : rows.getFirst();
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}")
  DraftResponse detail(@PathVariable UUID id) {
    return service.get(id);
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  Page<DraftResponse> search(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID quotationId,
      @RequestParam(defaultValue = "") String sender,
      @RequestParam(defaultValue = "") String recipient,
      @RequestParam(defaultValue = "") String subject,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.search(
        customerId, quotationId, sender, recipient, subject, status, from, to, page, size);
  }
}
