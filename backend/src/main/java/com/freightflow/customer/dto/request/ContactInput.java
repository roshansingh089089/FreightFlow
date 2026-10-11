package com.freightflow.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactInput(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank String phone,
    String designation) {}
