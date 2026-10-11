package com.freightflow.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quotation_email_logs")
@Getter
@Setter
public class QuotationEmailLog {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID quotationId;
  String recipient;
  String cc;
  String subject;
  UUID sentBy;
  String status;

  @Column(columnDefinition = "text")
  String errorMessage;

  Instant sentAt = Instant.now();
}
