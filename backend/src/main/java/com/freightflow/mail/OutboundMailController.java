package com.freightflow.mail;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/drafts")
class OutboundMailController {
  private final OutboundMailService service;

  OutboundMailController(OutboundMailService service) {
    this.service = service;
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  Page<DraftResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(status, page, size);
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}")
  DraftResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping
  DraftResponse create(@Valid @RequestBody DraftRequest r) {
    return service.create(r);
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @PutMapping("/{id}")
  DraftResponse update(@PathVariable UUID id, @Valid @RequestBody DraftRequest r) {
    return service.update(id, r);
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @DeleteMapping("/{id}")
  void discard(@PathVariable UUID id) {
    service.discard(id);
  }

  @PreAuthorize("@permissions.can('mail.send')")
  @PostMapping("/{id}/send")
  DraftResponse send(@PathVariable UUID id, @Valid @RequestBody SendDraftRequest r) {
    return service.queue(id, r.idempotencyKey());
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @PostMapping("/{id}/reconcile")
  DraftResponse reconcile(@PathVariable UUID id, @Valid @RequestBody ReconcileMailRequest request) {
    return service.reconcile(id, request);
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping("/reply/{messageId}")
  DraftResponse reply(@PathVariable UUID messageId) {
    return service.createReply(messageId, false);
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping("/reply-all/{messageId}")
  DraftResponse replyAll(@PathVariable UUID messageId) {
    return service.createReply(messageId, true);
  }
}
