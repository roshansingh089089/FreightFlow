package com.freightflow.quotation.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quotations")
@Getter
@Setter
public class Quotation extends BaseEntity {
  String quotationNumber;
  UUID enquiryId;
  UUID customerId;

  @Column(columnDefinition = "text")
  String shipmentSnapshot;

  @Column(columnDefinition = "text")
  String documentSnapshot;

  UUID sourceMessageId;
  UUID sourceThreadId;
  UUID sourceConnectionId;
  Instant fxFrozenAt;
  String currency;
  String transitTimeText;
  LocalDate validUntil;

  @Column(columnDefinition = "text")
  String termsAndConditions;

  @Column(columnDefinition = "text")
  String notes;

  BigDecimal totalAmount = BigDecimal.ZERO;
  String status = "DRAFT";
  UUID createdBy;
  Instant sentAt;
  @Version long version;
}
