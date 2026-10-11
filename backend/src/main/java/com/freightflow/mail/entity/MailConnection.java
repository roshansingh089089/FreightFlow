package com.freightflow.mail.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mail_connections")
@Getter
@Setter
public class MailConnection extends BaseEntity {
  UUID userId;
  String provider = "GMAIL_API";
  boolean active = true;
  String imapHost;
  Integer imapPort;
  String imapUsername;

  @Column(columnDefinition = "text")
  String imapPasswordEncrypted;

  String imapSecurityMode;
  String smtpHost;
  Integer smtpPort;
  String smtpUsername;

  @Column(columnDefinition = "text")
  String smtpPasswordEncrypted;

  String smtpSecurityMode;
  String smtpFrom;
  String emailAddress;

  @Column(columnDefinition = "text")
  String accessToken;

  @Column(columnDefinition = "text")
  String refreshToken;

  Instant tokenExpiry;
  String status = "CONNECTED";
  Instant lastSyncedAt;
  Long imapInboxUidValidity;
  Long imapInboxLastUid;
  String syncErrorCode;
  Instant syncLeaseUntil;
}
