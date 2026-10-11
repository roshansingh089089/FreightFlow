package com.freightflow.enquiry.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "enquiries")
@Getter
@Setter
public class Enquiry extends BaseEntity {
  String enquiryCode;
  UUID customerId;
  UUID contactId;
  String contactEmail;
  String mode;
  String origin;
  String destination;
  String portOfLoading;
  String portOfDischarge;
  String polCode;
  String podCode;
  String fpodCode;
  String airportOfOrigin;
  String airportOfDestination;
  String commodity;
  BigDecimal grossWeight;
  String weightUnit;
  BigDecimal volume;
  String volumeUnit;
  Integer packageCount;
  LocalDate readyDate;
  String incoterm;
  String cargoType;
  String containerType;
  Integer containerQuantity;
  boolean pickupRequired;
  boolean exportCustomsRequired;
  boolean insuranceRequired;
  boolean destinationDeliveryRequired;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  List<String> serviceScope;

  String quoteCurrency;
  boolean referenceDestinationCharges;

  @Column(columnDefinition = "text")
  String notes;

  String status = "NEW";
  UUID createdBy;
  UUID sourceMessageId;
  UUID sourceThreadId;
  UUID sourceConnectionId;
}
