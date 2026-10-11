package com.freightflow.mail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbound_messages")
@Getter
@Setter
public class OutboundMessage {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID createdBy;
  UUID connectionId;
  UUID threadId;
  UUID replyToMessageId;
  UUID replyToOutboundMessageId;
  String composeMode = "NEW";
  UUID customerId;
  UUID quotationId;
  String quotationSendKind;
  String status = "DRAFT";
  String fromAddress;
  String replyTo;
  String subject = "";

  @Column(columnDefinition = "text")
  String bodyText = "";

  String messageId;
  String inReplyTo;

  @Column(columnDefinition = "text")
  String referenceHeaders;

  String idempotencyKey;
  int sendAttempts;
  String errorCode;
  Instant createdAt = Instant.now();
  Instant updatedAt = Instant.now();
  Instant queuedAt;
  Instant sentAt;
  UUID reconciledBy;
  Instant reconciledAt;

  @Column(columnDefinition = "text")
  String reconciliationNote;

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }
}
