package com.freightflow.mail;

import com.freightflow.common.security.SecurityConfig;
import com.freightflow.mail.entity.MailThread;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{id}/mail-threads")
class CustomerMailThreadController {
  private final MailThreadService threads;

  CustomerMailThreadController(MailThreadService threads) {
    this.threads = threads;
  }

  @PreAuthorize("@permissions.can('mail.read') and @permissions.can('customer.read')")
  @GetMapping
  Page<MailThread> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return threads.customerHistory(SecurityConfig.current(), id, page, size);
  }
}
