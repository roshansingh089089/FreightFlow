package com.freightflow.customer;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{id}/conversations")
class CustomerConversationController {
  private final CustomerConversationService service;

  CustomerConversationController(CustomerConversationService service) {
    this.service = service;
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  List<CustomerConversation> list(
      @PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
    return service.customer(id, limit);
  }
}
