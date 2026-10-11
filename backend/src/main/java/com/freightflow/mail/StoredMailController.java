package com.freightflow.mail;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/stored")
class StoredMailController {
  private final OutboundMailService service;

  StoredMailController(OutboundMailService service) {
    this.service = service;
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
