package com.freightflow.customer.dto.request;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record ExtraCcRequest(@Email @NotBlank String email, String displayName) {}
