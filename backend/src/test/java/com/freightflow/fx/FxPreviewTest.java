package com.freightflow.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class FxPreviewTest {
  @Test
  void previewUsesTheSameLineConversionAndRoundingAsQuotationSaving() {
    ExchangeRateService rates = Mockito.mock(ExchangeRateService.class);
    when(rates.rate("INR", "THB"))
        .thenReturn(
            new ExchangeRateService.Rate(
                new BigDecimal("0.3466348083"), "ECB_REFERENCE", LocalDate.of(2026, 10, 9)));
    when(rates.rate("THB", "THB"))
        .thenReturn(new ExchangeRateService.Rate(BigDecimal.ONE, "SAME_CURRENCY", LocalDate.now()));
    var controller =
        new FxController(
            Mockito.mock(JdbcTemplate.class),
            new CurrencyConversionService(rates, BigDecimal.ZERO));
    var result =
        controller.preview(
            new FxController.PreviewInput(
                "THB",
                List.of(
                    new FxController.PreviewLine(new BigDecimal("2"), new BigDecimal("100"), "INR"),
                    new FxController.PreviewLine(BigDecimal.ONE, new BigDecimal("10"), "THB"))));
    assertEquals(new BigDecimal("69.32"), result.lines().getFirst().convertedAmount());
    assertEquals(new BigDecimal("79.32"), result.total());
    assertEquals("ECB_REFERENCE", result.lines().getFirst().source());
  }
}
