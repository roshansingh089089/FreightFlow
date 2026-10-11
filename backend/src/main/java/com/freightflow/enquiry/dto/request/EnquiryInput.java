package com.freightflow.enquiry.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EnquiryInput(
    @NotNull UUID customerId,
    UUID contactId,
    @Email String contactEmail,
    @NotBlank String mode,
    @NotBlank String origin,
    @NotBlank String destination,
    String portOfLoading,
    String portOfDischarge,
    String airportOfOrigin,
    String airportOfDestination,
    @NotBlank String commodity,
    @DecimalMin("0") BigDecimal grossWeight,
    String weightUnit,
    @DecimalMin("0") BigDecimal volume,
    String volumeUnit,
    @Min(0) Integer packageCount,
    LocalDate readyDate,
    String incoterm,
    String cargoType,
    String containerType,
    @Min(0) Integer containerQuantity,
    boolean pickupRequired,
    boolean exportCustomsRequired,
    boolean insuranceRequired,
    boolean destinationDeliveryRequired,
    String notes,
    String status,
    UUID sourceMessageId,
    String polCode,
    String podCode,
    String fpodCode,
    List<String> serviceScope,
    String quoteCurrency,
    boolean referenceDestinationCharges) {}
