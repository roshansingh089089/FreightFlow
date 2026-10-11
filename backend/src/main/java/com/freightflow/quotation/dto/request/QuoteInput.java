package com.freightflow.quotation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record QuoteInput(
    @NotBlank String currency,
    String transitTimeText,
    @NotNull @FutureOrPresent LocalDate validUntil,
    @NotBlank String termsAndConditions,
    String notes,
    List<@Valid LineInput> lines,
    @Valid ShipmentOverrideInput shipmentOverride) {}
