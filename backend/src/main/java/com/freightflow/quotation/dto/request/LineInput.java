package com.freightflow.quotation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LineInput(
    @NotBlank String description,
    @NotNull @DecimalMin("0.01") BigDecimal quantity,
    @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
    String currency,
    String pricingTreatment) {
  public LineInput(String description, BigDecimal quantity, BigDecimal unitPrice, String currency) {
    this(description, quantity, unitPrice, currency, null);
  }
}
