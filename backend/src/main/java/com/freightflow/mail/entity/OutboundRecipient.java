package com.freightflow.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbound_recipients")
@Getter
@Setter
public class OutboundRecipient {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID messageId;
  String recipientType;
  String email;
  String displayName;
}
