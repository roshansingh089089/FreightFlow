package com.freightflow.fx;

import com.freightflow.common.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx")
class FxController {
  private final JdbcTemplate jdbc;
  private final CurrencyConversionService conversions;

  FxController(JdbcTemplate jdbc, CurrencyConversionService conversions) {
    this.jdbc = jdbc;
    this.conversions = conversions;
  }

  record PreviewLine(
      @NotNull @DecimalMin("0.01") BigDecimal quantity,
      @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
      @NotBlank String currency) {}

  record PreviewInput(@NotBlank String currency, @NotEmpty List<@Valid PreviewLine> lines) {}

  record ConvertedLine(
      BigDecimal originalAmount,
      String originalCurrency,
      BigDecimal convertedAmount,
      BigDecimal rate,
      BigDecimal referenceRate,
      String source,
      LocalDate observationDate,
      BigDecimal bufferPercent) {}

  record PreviewResult(String currency, List<ConvertedLine> lines, BigDecimal total) {}

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/quote-preview")
  PreviewResult preview(@Valid @RequestBody PreviewInput input) {
    String currency = ExchangeRateService.validCurrency(input.currency());
    List<ConvertedLine> lines =
        input.lines().stream()
            .map(
                line -> {
                  var converted = conversions.convert(line.unitPrice(), line.currency(), currency);
                  return new ConvertedLine(
                      line.unitPrice().multiply(line.quantity()),
                      converted.originalCurrency(),
                      converted
                          .convertedAmount()
                          .multiply(line.quantity())
                          .setScale(2, RoundingMode.HALF_UP),
                      converted.rate(),
                      converted.referenceRate(),
                      converted.source(),
                      converted.observationDate(),
                      converted.bufferPercent());
                })
            .toList();
    BigDecimal total =
        lines.stream().map(ConvertedLine::convertedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PreviewResult(currency, lines, total);
  }

  record ManualRateInput(
      @NotBlank String fromCurrency,
      @NotBlank String toCurrency,
      @NotNull @DecimalMin("0.0000000001") BigDecimal rate,
      @NotNull LocalDate effectiveDate,
      @NotBlank String reason) {}

  @PreAuthorize("@permissions.can('fx.manual')")
  @PostMapping("/manual-rates")
  Map<String, Object> manual(@Valid @RequestBody ManualRateInput input) {
    var user = SecurityConfig.current();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into fx_manual_rates(id,tenant_id,from_currency,to_currency,rate,effective_date,reason,created_by) "
            + "values (?,?,?,?,?,?,?,?)",
        id,
        user.getTenantId(),
        ExchangeRateService.validCurrency(input.fromCurrency()),
        ExchangeRateService.validCurrency(input.toCurrency()),
        input.rate(),
        input.effectiveDate(),
        input.reason().trim(),
        user.getId());
    return Map.of("id", id, "source", "MANUAL");
  }
}
