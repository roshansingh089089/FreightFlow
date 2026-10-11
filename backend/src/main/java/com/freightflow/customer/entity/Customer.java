package com.freightflow.customer.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer extends BaseEntity {
  String customerCode;
  String companyName;
  String customerType;
  String country;
  String gstin;
  String iec;
  String status = "ACTIVE";
  UUID salesOwnerUserId;
  Integer creditTermsDays;
  BigDecimal creditLimit;
  Instant archivedAt;
}
