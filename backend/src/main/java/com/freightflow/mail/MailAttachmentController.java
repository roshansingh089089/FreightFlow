package com.freightflow.mail;

import com.freightflow.mail.entity.OutboundAttachment;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mail/attachments")
class MailAttachmentController {
  private final MailAttachmentService service;

  MailAttachmentController(MailAttachmentService service) {
    this.service = service;
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
    OutboundAttachment a = service.upload(file);
    return Map.of(
        "id",
        a.getId(),
        "filename",
        a.getFilename(),
        "mediaType",
        a.getMediaType(),
        "sizeBytes",
        a.getSizeBytes());
  }

  @PreAuthorize("@permissions.can('mail.read')")
  @GetMapping("/{id}")
  ResponseEntity<byte[]> download(@PathVariable UUID id) {
    OutboundAttachment a = service.owned(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(a.getMediaType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + a.getFilename().replace("\"", "") + "\"")
        .body(service.bytes(a));
  }

  @PreAuthorize("@permissions.can('mail.compose')")
  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
