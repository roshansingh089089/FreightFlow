package com.freightflow.customer.dto.response;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record AddressView(
    UUID id,
    String addressType,
    String line1,
    String line2,
    String city,
    String state,
    String postalCode,
    String country) {}
