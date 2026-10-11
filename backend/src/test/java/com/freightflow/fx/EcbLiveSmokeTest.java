package com.freightflow.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class EcbLiveSmokeTest {
  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_LIVE_ECB", matches = "true")
  void fetchesInrAndThbOverVerifiedTls() {
    var observations = new EcbExchangeRateProvider().latest();
    assertTrue(observations.get("INR").unitsPerEur().compareTo(BigDecimal.ZERO) > 0);
    assertTrue(observations.get("THB").unitsPerEur().compareTo(BigDecimal.ZERO) > 0);
    assertEquals(observations.get("INR").date(), observations.get("THB").date());
  }
}
