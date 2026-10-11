package com.freightflow.customer.dto.response;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record ContactView(
    UUID id,
    String name,
    String email,
    String phone,
    String designation,
    String contactRole,
    boolean primary,
    boolean autoCc,
    boolean active) {}
