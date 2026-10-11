package com.freightflow.quotation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quotation_lines")
@Getter
@Setter
public class QuotationLine {
  @Id UUID id = UUID.randomUUID();
  UUID tenantId;
  UUID quotationId;
  String description;
  BigDecimal quantity;
  BigDecimal unitPrice;
  BigDecimal amount;
  String pricingTreatment = "INCLUDED";
  Integer sortOrder;
  String originalCurrency;
  BigDecimal originalUnitPrice;
  BigDecimal fxRate;
  BigDecimal fxReferenceRate;
  String fxSource;
  LocalDate fxObservationDate;
  BigDecimal fxBufferPercent;
}
