package com.freightflow.customer.dto.response;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record ExtraCcView(UUID id, String email, String displayName, boolean active) {}
