package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.InboundAttachment;
import com.freightflow.mail.repository.EmailMessageRepo;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail/messages/{messageId}/attachments")
class InboundAttachmentController {
  private final MailService mail;
  private final EmailMessageRepo messages;
  private final InboundAttachmentService attachments;

  InboundAttachmentController(
      MailService mail, EmailMessageRepo messages, InboundAttachmentService attachments) {
    this.mail = mail;
    this.messages = messages;
    this.attachments = attachments;
  }

  record View(UUID id, String filename, String mediaType, long sizeBytes) {
    static View of(InboundAttachment a) {
      return new View(a.getId(), a.getFilename(), a.getMediaType(), a.getSizeBytes());
    }
  }

  private EmailMessage owned(UUID id) {
    var user = SecurityConfig.current();
    var connection = mail.active(user);
    return messages
        .findByIdAndTenantIdAndConnectionId(id, user.getTenantId(), connection.getId())
        .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Email not found"));
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping
  List<View> list(@PathVariable UUID messageId) {
    return attachments.list(owned(messageId)).stream().map(View::of).toList();
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}")
  ResponseEntity<byte[]> download(@PathVariable UUID messageId, @PathVariable UUID id) {
    InboundAttachment attachment = attachments.get(owned(messageId), id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(attachment.getMediaType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + attachment.getFilename().replace("\"", "") + "\"")
        .header("X-Content-Type-Options", "nosniff")
        .body(attachments.bytes(attachment));
  }
}
