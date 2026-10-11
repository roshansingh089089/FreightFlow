package com.freightflow.common.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "business_audit_events")
@Getter
@Setter
public class BusinessAuditEvent {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID actorUserId;
  String entityType;
  UUID entityId;
  String action;

  @Column(columnDefinition = "text")
  String detail;

  Instant createdAt = Instant.now();
}
