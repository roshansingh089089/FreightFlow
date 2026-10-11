package com.freightflow.mail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "email_messages")
@Getter
@Setter
public class EmailMessage {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID connectionId;
  UUID threadId;
  String providerMessageId;
  String providerThreadId;
  String rfcMessageId;
  String inReplyTo;

  @Column(columnDefinition = "text")
  String referenceHeaders;

  String fromName;
  String fromEmail;

  @Column(columnDefinition = "text")
  String replyToEmail;

  @Column(columnDefinition = "text")
  String toEmail;

  @Column(columnDefinition = "text")
  String ccEmail;

  Long imapUid;
  Long imapUidValidity;
  String imapFolder = "INBOX";
  boolean inInbox = true;

  @Column(columnDefinition = "text")
  String subject;

  @Column(columnDefinition = "text")
  String bodyText;

  Instant receivedAt;

  @Column(name = "is_read")
  boolean read;

  Instant createdAt = Instant.now();
}
