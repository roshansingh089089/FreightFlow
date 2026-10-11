package com.freightflow.tenant.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {
  @Id UUID id = UUID.randomUUID();
  String companyName;
  String country;
  String timezone = "UTC";
  String defaultCurrency = "USD";
  String plan = "FREE";
  String status = "ACTIVE";
  Instant createdAt = Instant.now();
  Instant updatedAt = Instant.now();
}
