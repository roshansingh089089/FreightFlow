package com.freightflow.mail;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{id}/sent-mail")
class CustomerSentMailController {
  private final OutboundMailService service;

  CustomerSentMailController(OutboundMailService service) {
    this.service = service;
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  Page<DraftResponse> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.customerHistory(id, page, size);
  }
}
