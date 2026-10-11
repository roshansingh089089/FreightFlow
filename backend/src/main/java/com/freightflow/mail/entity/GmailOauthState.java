package com.freightflow.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gmail_oauth_states")
@Getter
@Setter
public class GmailOauthState {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID userId;
  String stateHash;
  Instant expiresAt;
  Instant createdAt = Instant.now();
}
