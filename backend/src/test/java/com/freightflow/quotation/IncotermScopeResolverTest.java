package com.freightflow.quotation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.freightflow.common.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncotermScopeResolverTest {
  @Test
  void supportsAllTermsAndCoreCharges() {
    for (String term :
        List.of("EXW", "FCA", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP"))
      assertTrue(IncotermScopeResolver.defaults(term, "FCL", false).contains("Ocean Freight"));
    assertTrue(
        IncotermScopeResolver.defaults("EXW", "FCL", false).contains(IncotermScopeResolver.PICKUP));
    assertTrue(
        IncotermScopeResolver.defaults("CIF", "FCL", false)
            .contains(IncotermScopeResolver.INSURANCE));
    assertTrue(
        IncotermScopeResolver.defaults("DDP", "FCL", false).contains(IncotermScopeResolver.DUTIES));
    assertThrows(ApiException.class, () -> IncotermScopeResolver.defaults("BAD", "FCL", false));
  }

  @Test
  void finalPlaceControlsHaulageAndRoadScope() {
    assertFalse(
        IncotermScopeResolver.defaults("DAP", "FCL", false)
            .contains(IncotermScopeResolver.HAULAGE));
    assertTrue(
        IncotermScopeResolver.defaults("DAP", "FCL", true).contains(IncotermScopeResolver.HAULAGE));
    var road = IncotermScopeResolver.defaults("DDP", "FTL", true);
    assertTrue(road.contains("Road freight"));
    assertFalse(road.contains(IncotermScopeResolver.HAULAGE));
    assertFalse(road.contains(IncotermScopeResolver.DESTINATION));
    assertThrows(
        ApiException.class,
        () ->
            IncotermScopeResolver.validateScope(
                List.of(IncotermScopeResolver.HAULAGE), "FTL", true));
  }

  @Test
  void referenceDestinationIsLimitedToFourTerms() {
    for (String term : List.of("CFR", "CIF", "CPT", "CIP"))
      assertTrue(IncotermScopeResolver.referenceDestinationAllowed(term));
    assertFalse(IncotermScopeResolver.referenceDestinationAllowed("FOB"));
  }

  @Test
  void optionalSalesTrackerServicesRemainValidInQuotationScope() {
    IncotermScopeResolver.validateScope(
        List.of("Ocean Freight", "Fumigation", "Certificate of Origin", "Warehousing"),
        "FCL",
        false);
    assertThrows(
        ApiException.class,
        () -> IncotermScopeResolver.validateScope(List.of("Unknown service"), "FCL", false));
  }
}
