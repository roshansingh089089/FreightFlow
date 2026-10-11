package com.freightflow.quotation.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ShipmentOverrideInput(
    String polCode,
    String podCode,
    String fpodCode,
    String incoterm,
    String shipmentMode,
    String customerReference,
    LocalDate cargoReadyDate,
    String commodity,
    Boolean dangerousGoods,
    Integer freeDaysAtDestination,
    String containerType,
    Integer containerQuantity,
    BigDecimal grossWeightPerContainerKg,
    Integer packageCount,
    BigDecimal cbm,
    BigDecimal weightKg,
    Boolean stackable,
    Integer pieces,
    BigDecimal chargeableWeightKg,
    String truckType,
    Integer truckCount,
    List<String> scope) {
  public ShipmentOverrideInput(String polCode, String podCode, String fpodCode, String incoterm) {
    this(
        polCode, podCode, fpodCode, incoterm, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);
  }
}
