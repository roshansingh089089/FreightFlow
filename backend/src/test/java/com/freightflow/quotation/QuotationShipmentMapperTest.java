package com.freightflow.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.common.exception.ApiException;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.quotation.dto.request.ShipmentOverrideInput;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.reference.Location;
import com.freightflow.reference.LocationSearchService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QuotationShipmentMapperTest {
  @Test
  void snapshotsSupportedFieldsAndSourceThreadWithoutPrices() throws Exception {
    var mapper = new QuotationShipmentMapper(new ObjectMapper().findAndRegisterModules());
    Enquiry enquiry = new Enquiry();
    enquiry.setId(UUID.randomUUID());
    enquiry.setCustomerId(UUID.randomUUID());
    enquiry.setMode("OCEAN");
    enquiry.setOrigin("Laem Chabang");
    enquiry.setDestination("Nhava Sheva");
    enquiry.setPolCode("THLCH");
    enquiry.setPodCode("INNSA");
    enquiry.setContainerType("40HC");
    enquiry.setContainerQuantity(2);
    enquiry.setReadyDate(LocalDate.of(2026, 10, 20));
    enquiry.setSourceMessageId(UUID.randomUUID());
    enquiry.setSourceThreadId(UUID.randomUUID());
    Quotation quotation = new Quotation();
    mapper.snapshot(enquiry, quotation);
    mapper.requireCanonicalRoute(quotation);
    var data = new ObjectMapper().readTree(quotation.getShipmentSnapshot());
    assertEquals("THLCH", data.path("polCode").asText());
    assertEquals("40HC", data.path("containerType").asText());
    assertEquals(2, data.path("containerQuantity").asInt());
    assertEquals("2026-10-20", data.path("readyDate").asText());
    assertTrue(!data.has("freightPrice"));
    assertEquals(enquiry.getSourceThreadId(), quotation.getSourceThreadId());
  }

  @Test
  void snapshotsSalesTrackerScopeAndCurrencyForQuotation() throws Exception {
    var mapper = new QuotationShipmentMapper(new ObjectMapper().findAndRegisterModules());
    Enquiry enquiry = new Enquiry();
    enquiry.setMode("OCEAN");
    enquiry.setIncoterm("CIF");
    enquiry.setServiceScope(
        List.of("Ocean Freight", "Origin charges", "Cargo insurance", "Warehousing"));
    enquiry.setQuoteCurrency("THB");
    enquiry.setReferenceDestinationCharges(true);
    Quotation quotation = new Quotation();

    mapper.snapshot(enquiry, quotation);

    var data = new ObjectMapper().readTree(quotation.getShipmentSnapshot());
    assertEquals("THB", data.path("quoteCurrency").asText());
    assertTrue(data.path("referenceDestinationCharges").asBoolean());
    assertEquals(4, data.path("scope").size());
    assertTrue(data.path("scopeCustomized").asBoolean());
  }

  @Test
  void rejectsUnknownRouteAndAllowsExplicitQuotationOverride() throws Exception {
    var mapper = new QuotationShipmentMapper(new ObjectMapper().findAndRegisterModules());
    Enquiry enquiry = new Enquiry();
    enquiry.setMode("OCEAN");
    Quotation quotation = new Quotation();
    mapper.snapshot(enquiry, quotation);
    assertThrows(ApiException.class, () -> mapper.requireCanonicalRoute(quotation));
    LocationSearchService locations = Mockito.mock(LocationSearchService.class);
    Location pol = new Location();
    pol.setCode("THLCH");
    pol.setName("Laem Chabang");
    Location pod = new Location();
    pod.setCode("INNSA");
    pod.setName("Nhava Sheva (Jawaharlal Nehru)");
    when(locations.require("THLCH", "OCEAN")).thenReturn(pol);
    when(locations.require("INNSA", "OCEAN")).thenReturn(pod);
    mapper.applyOverride(
        quotation, new ShipmentOverrideInput("THLCH", "INNSA", null, "FOB"), locations);
    mapper.requireCanonicalRoute(quotation);
    var data = new ObjectMapper().readTree(quotation.getShipmentSnapshot());
    assertEquals("Nhava Sheva (Jawaharlal Nehru)", data.path("destination").asText());
    assertEquals("FOB", data.path("incoterm").asText());
  }

  @Test
  void changesIncotermWithoutRouteOverrideAndRejectsUnsupportedTerms() throws Exception {
    var mapper = new QuotationShipmentMapper(new ObjectMapper().findAndRegisterModules());
    Enquiry enquiry = new Enquiry();
    enquiry.setMode("ROAD");
    enquiry.setIncoterm("EXW");
    Quotation quotation = new Quotation();
    mapper.snapshot(enquiry, quotation);
    LocationSearchService locations = Mockito.mock(LocationSearchService.class);

    mapper.applyOverride(quotation, new ShipmentOverrideInput(null, null, null, "DDP"), locations);
    assertEquals(
        "DDP",
        new ObjectMapper().readTree(quotation.getShipmentSnapshot()).path("incoterm").asText());
    assertThrows(
        ApiException.class,
        () ->
            mapper.applyOverride(
                quotation, new ShipmentOverrideInput(null, null, null, "INVALID"), locations));
  }

  @Test
  void validatesRequiredFieldsAndCalculatesLclWeightMeasure() throws Exception {
    var json = new ObjectMapper().findAndRegisterModules();
    var mapper = new QuotationShipmentMapper(json);
    Enquiry enquiry = new Enquiry();
    enquiry.setMode("OCEAN");
    enquiry.setPolCode("THLCH");
    enquiry.setPodCode("INNSA");
    enquiry.setIncoterm("CIF");
    Quotation quotation = new Quotation();
    mapper.snapshot(enquiry, quotation);
    assertThrows(ApiException.class, () -> mapper.validate(quotation));
    var lcl =
        json.readValue(
            "{\"shipmentMode\":\"LCL\",\"incoterm\":\"CIF\",\"cbm\":2.5,\"weightKg\":4000}",
            ShipmentOverrideInput.class);
    mapper.applyOverride(quotation, lcl, Mockito.mock(LocationSearchService.class));
    mapper.validate(quotation);
    assertEquals(
        4.0, json.readTree(quotation.getShipmentSnapshot()).path("weightMeasure").asDouble());
    assertTrue(mapper.referenceChargeAllowed(quotation, IncotermScopeResolver.DESTINATION));
    assertTrue(!mapper.referenceChargeAllowed(quotation, IncotermScopeResolver.ORIGIN));
  }

  @Test
  void requiresFtlTruckAndPreservesCustomScopeAcrossIncotermChange() throws Exception {
    var json = new ObjectMapper().findAndRegisterModules();
    var mapper = new QuotationShipmentMapper(json);
    Enquiry enquiry = new Enquiry();
    enquiry.setMode("ROAD");
    enquiry.setPolCode("A");
    enquiry.setPodCode("B");
    enquiry.setIncoterm("EXW");
    Quotation quotation = new Quotation();
    mapper.snapshot(enquiry, quotation);
    assertThrows(ApiException.class, () -> mapper.validate(quotation));
    var custom =
        json.readValue(
            "{\"shipmentMode\":\"FTL\",\"truckType\":\"Box truck\",\"truckCount\":2,\"scope\":[\"Road freight\"]}",
            ShipmentOverrideInput.class);
    mapper.applyOverride(quotation, custom, Mockito.mock(LocationSearchService.class));
    mapper.validate(quotation);
    mapper.applyOverride(
        quotation,
        new ShipmentOverrideInput(null, null, null, "DDP"),
        Mockito.mock(LocationSearchService.class));
    assertEquals(
        "Road freight",
        json.readTree(quotation.getShipmentSnapshot()).path("scope").get(0).asText());
  }
}
