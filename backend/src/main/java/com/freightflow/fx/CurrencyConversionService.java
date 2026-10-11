package com.freightflow.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrencyConversionService {
  private final ExchangeRateService rates;
  private final BigDecimal bufferPercent;

  public CurrencyConversionService(
      ExchangeRateService rates, @Value("${fx.buffer-percent:0}") BigDecimal bufferPercent) {
    if (bufferPercent.signum() < 0 || bufferPercent.compareTo(new BigDecimal("25")) > 0)
      throw new IllegalArgumentException("FX buffer percent must be between 0 and 25");
    this.rates = rates;
    this.bufferPercent = bufferPercent;
  }

  public record Conversion(
      BigDecimal originalAmount,
      String originalCurrency,
      BigDecimal convertedAmount,
      BigDecimal rate,
      BigDecimal referenceRate,
      String source,
      LocalDate observationDate,
      BigDecimal bufferPercent) {}

  public Conversion convert(BigDecimal amount, String from, String to) {
    ExchangeRateService.Rate rate = rates.rate(from, to);
    BigDecimal appliedBuffer =
        "SAME_CURRENCY".equals(rate.source()) ? BigDecimal.ZERO : bufferPercent;
    BigDecimal adjusted =
        rate.value()
            .multiply(
                BigDecimal.ONE.add(
                    appliedBuffer.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)));
    return new Conversion(
        amount,
        ExchangeRateService.validCurrency(from),
        amount.multiply(adjusted).setScale(2, RoundingMode.HALF_UP),
        adjusted,
        rate.value(),
        rate.source(),
        rate.date(),
        appliedBuffer);
  }
}
