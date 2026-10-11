package com.freightflow.customer.dto.response;

import com.freightflow.customer.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record CustomerActivityView(
    UUID id, String action, String detail, Instant createdAt, UUID actorUserId) {}
