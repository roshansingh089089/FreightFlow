package com.freightflow.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbound_attachments")
@Getter
@Setter
public class OutboundAttachment {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID messageId;
  UUID uploadedBy;
  String filename;
  String mediaType;
  long sizeBytes;
  String storageKey;
  String sha256;
  Instant createdAt = Instant.now();
}
