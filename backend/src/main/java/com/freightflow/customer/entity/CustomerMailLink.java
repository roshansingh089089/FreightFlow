package com.freightflow.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_mail_links")
@Getter
@Setter
public class CustomerMailLink {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID customerId;
  UUID messageId;
  UUID linkedBy;
  Instant linkedAt = Instant.now();
}
