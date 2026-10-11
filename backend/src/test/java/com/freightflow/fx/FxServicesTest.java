package com.freightflow.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.freightflow.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FxServicesTest {
  @Test
  void parsesOfficialEcbCsvColumns() {
    var rates =
        EcbExchangeRateProvider.parse(
            "KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE\n"
                + "EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-10-09,1.1206\n"
                + "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,2026-10-09,0.84763\n");
    assertEquals(new BigDecimal("1.1206"), rates.get("USD").unitsPerEur());
    assertEquals(LocalDate.of(2026, 10, 9), rates.get("GBP").date());
  }

  @Test
  void roundsConvertedAmountAndDoesNotBufferSameCurrency() {
    ExchangeRateService rates = Mockito.mock(ExchangeRateService.class);
    when(rates.rate("USD", "GBP"))
        .thenReturn(
            new ExchangeRateService.Rate(
                new BigDecimal("0.75639836"), "ECB_REFERENCE", LocalDate.of(2026, 10, 9)));
    when(rates.rate("GBP", "GBP"))
        .thenReturn(new ExchangeRateService.Rate(BigDecimal.ONE, "SAME_CURRENCY", LocalDate.now()));
    var conversion = new CurrencyConversionService(rates, new BigDecimal("2"));
    var converted = conversion.convert(new BigDecimal("100.00"), "USD", "GBP");
    assertEquals(new BigDecimal("77.15"), converted.convertedAmount());
    assertEquals(new BigDecimal("2"), converted.bufferPercent());
    assertEquals(
        new BigDecimal("100.00"),
        conversion.convert(new BigDecimal("100.00"), "GBP", "GBP").convertedAmount());
  }

  @Test
  void rejectsInvalidCurrencies() {
    assertThrows(ApiException.class, () -> ExchangeRateService.validCurrency("US dollars"));
  }

  @Test
  void crossRatesUseCommonEurBaseAndRejectStaleObservations() {
    assertEquals(
        new BigDecimal("0.7500000000"),
        ExchangeRateService.crossRate(new BigDecimal("1.2"), new BigDecimal("0.9")));
    assertEquals(
        true, ExchangeRateService.isFresh(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 11)));
    assertEquals(
        false, ExchangeRateService.isFresh(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 11)));
  }
}
