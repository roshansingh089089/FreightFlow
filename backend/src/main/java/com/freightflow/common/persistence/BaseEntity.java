package com.freightflow.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID tenantId;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  @Column(nullable = false)
  Instant updatedAt = Instant.now();

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }
}
