package com.freightflow.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerInput(
    @NotBlank String companyName,
    @NotBlank String customerType,
    @NotBlank String country,
    String gstin,
    String iec,
    String status,
    @Valid @NotNull ContactInput primaryContact,
    List<@Valid AddressInput> addresses,
    UUID salesOwnerUserId,
    @Min(0) Integer creditTermsDays,
    @DecimalMin("0") BigDecimal creditLimit) {}
