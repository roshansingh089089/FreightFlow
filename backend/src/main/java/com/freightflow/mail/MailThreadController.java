package com.freightflow.mail;

import com.freightflow.common.security.SecurityConfig;
import com.freightflow.mail.entity.MailThread;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/threads")
class MailThreadController {
  private final MailService mail;
  private final MailThreadService threads;

  MailThreadController(MailService mail, MailThreadService threads) {
    this.mail = mail;
    this.threads = threads;
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  Page<MailThread> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var user = SecurityConfig.current();
    return threads.list(user, mail.active(user), page, size);
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}")
  MailThread get(@PathVariable UUID id) {
    var user = SecurityConfig.current();
    return threads.owned(user, mail.active(user), id);
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}/messages")
  List<Object> messages(@PathVariable UUID id) {
    var user = SecurityConfig.current();
    return threads.messages(user, mail.active(user), id);
  }

  record CustomerLinkRequest(UUID customerId) {}

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping("/{id}/customer")
  MailThread link(@PathVariable UUID id, @RequestBody CustomerLinkRequest request) {
    var user = SecurityConfig.current();
    return threads.linkCustomer(user, mail.active(user), id, request.customerId());
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @DeleteMapping("/{id}/customer")
  void unlink(@PathVariable UUID id) {
    var user = SecurityConfig.current();
    threads.unlinkCustomer(user, mail.active(user), id);
  }
}
