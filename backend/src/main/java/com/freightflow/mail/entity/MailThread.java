package com.freightflow.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mail_threads")
@Getter
@Setter
public class MailThread {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId;
  private UUID connectionId;
  private UUID customerId;
  private String normalizedSubject = "";
  private String resolutionStatus = "NORMAL";
  private Instant createdAt = Instant.now();
  private Instant lastActivityAt = Instant.now();
}
