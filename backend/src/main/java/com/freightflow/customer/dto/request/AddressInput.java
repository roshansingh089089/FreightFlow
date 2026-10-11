package com.freightflow.customer.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddressInput(
    @NotBlank String addressType,
    @NotBlank String line1,
    String line2,
    @NotBlank String city,
    String state,
    String postalCode,
    @NotBlank String country) {}
