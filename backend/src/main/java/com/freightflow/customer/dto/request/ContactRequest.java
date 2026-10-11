package com.freightflow.customer.dto.request;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record ContactRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank String phone,
    String designation,
    String role,
    boolean primary,
    boolean autoCc,
    boolean active) {}
