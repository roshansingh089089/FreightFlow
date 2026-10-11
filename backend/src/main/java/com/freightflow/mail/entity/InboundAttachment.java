package com.freightflow.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inbound_mail_attachments")
@Getter
@Setter
public class InboundAttachment {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId;
  private UUID connectionId;
  private UUID messageId;
  private String filename;
  private String mediaType;
  private long sizeBytes;
  private String storageKey;
  private String sha256;
  private Instant createdAt = Instant.now();
}
