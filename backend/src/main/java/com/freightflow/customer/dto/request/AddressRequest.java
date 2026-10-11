package com.freightflow.customer.dto.request;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record AddressRequest(
    @NotBlank String addressType,
    @NotBlank String line1,
    String line2,
    @NotBlank String city,
    String state,
    String postalCode,
    @NotBlank String country) {}
