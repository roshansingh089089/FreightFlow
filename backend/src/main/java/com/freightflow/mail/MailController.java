package com.freightflow.mail;

import com.freightflow.common.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
class MailController {
  private final MailService mail;

  MailController(MailService mail) {
    this.mail = mail;
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @PostMapping("/sync")
  MailSyncResult sync() {
    return mail.sync(SecurityConfig.current());
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/messages")
  List<GmailIntegration.MessageList> list() {
    return mail.list(SecurityConfig.current());
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/counts")
  MailService.MailCounts counts() {
    return mail.counts(SecurityConfig.current());
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/messages/{id}")
  GmailIntegration.MessageDetail detail(@PathVariable UUID id) {
    return mail.detail(SecurityConfig.current(), id);
  }
}
