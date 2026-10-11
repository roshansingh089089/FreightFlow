package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.InboundAttachment;
import com.freightflow.mail.repository.InboundAttachmentRepo;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
class InboundAttachmentService {
  private final InboundAttachmentRepo attachments;
  private final MailAttachmentStorage storage;

  InboundAttachmentService(
      InboundAttachmentRepo attachments, ObjectProvider<MailAttachmentStorage> storage) {
    this.attachments = attachments;
    this.storage = storage.getIfAvailable();
  }

  void capture(Part part, EmailMessage message) throws Exception {
    if (storage == null) return;
    List<String> stored = new ArrayList<>();
    try {
      captureParts(part, message, stored);
    } catch (Exception e) {
      for (String key : stored) {
        try {
          storage.delete(key);
        } catch (Exception ignored) {
        }
      }
      throw e;
    }
  }

  private void captureParts(Part part, EmailMessage message, List<String> stored) throws Exception {
    if (part.getFileName() != null) {
      String name = safeName(MimeUtility.decodeText(part.getFileName()));
      if (name.isBlank()) return;
      byte[] bytes;
      try (var input = part.getInputStream()) {
        bytes = input.readNBytes(10_485_761);
      }
      if (bytes.length == 0 || bytes.length > 10_485_760) return;
      String media = MailAttachmentService.detect(bytes);
      if (media == null) return;
      String key = UUID.randomUUID().toString();
      stored.add(key);
      storage.put(key, bytes);
      InboundAttachment row = new InboundAttachment();
      row.setTenantId(message.getTenantId());
      row.setConnectionId(message.getConnectionId());
      row.setMessageId(message.getId());
      row.setFilename(name);
      row.setMediaType(media);
      row.setSizeBytes(bytes.length);
      row.setStorageKey(key);
      row.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      attachments.save(row);
      return;
    }
    if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++)
        captureParts(multipart.getBodyPart(i), message, stored);
    }
  }

  private static String safeName(String original) {
    if (original == null) return "";
    String name = original.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
    if (name.equals(".") || name.equals("..")) return "";
    return name.length() > 255 ? name.substring(0, 255) : name;
  }

  List<InboundAttachment> list(EmailMessage message) {
    return attachments.findByTenantIdAndConnectionIdAndMessageId(
        message.getTenantId(), message.getConnectionId(), message.getId());
  }

  InboundAttachment get(EmailMessage message, UUID id) {
    return attachments
        .findByIdAndTenantIdAndConnectionIdAndMessageId(
            id, message.getTenantId(), message.getConnectionId(), message.getId())
        .orElseThrow(
            () -> new ApiException(404, "MAIL_ATTACHMENT_NOT_FOUND", "Attachment not found"));
  }

  byte[] bytes(InboundAttachment attachment) {
    if (storage == null)
      throw new ApiException(
          503,
          "MAIL_ATTACHMENT_STORAGE_UNAVAILABLE",
          "Private attachment storage is not configured");
    try {
      return storage.get(attachment.getStorageKey());
    } catch (Exception e) {
      throw new ApiException(503, "MAIL_ATTACHMENT_UNAVAILABLE", "Attachment could not be read");
    }
  }
}
