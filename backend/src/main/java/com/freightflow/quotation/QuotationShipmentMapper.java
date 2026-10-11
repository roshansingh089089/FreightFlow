package com.freightflow.quotation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.freightflow.common.exception.ApiException;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.quotation.dto.request.ShipmentOverrideInput;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.reference.LocationSearchService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class QuotationShipmentMapper {
  private final ObjectMapper mapper;

  public QuotationShipmentMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void snapshot(Enquiry enquiry, Quotation quotation) {
    var details = new LinkedHashMap<String, Object>();
    details.put("enquiryId", enquiry.getId());
    details.put("customerId", enquiry.getCustomerId());
    details.put("contactId", enquiry.getContactId());
    details.put("contactEmail", enquiry.getContactEmail());
    details.put("mode", enquiry.getMode());
    details.put("shipmentMode", defaultShipmentMode(enquiry.getMode(), enquiry.getCargoType()));
    details.put("customerReference", (String) null);
    details.put("dangerousGoods", false);
    details.put("freeDaysAtDestination", (Integer) null);
    details.put("origin", enquiry.getOrigin());
    details.put("destination", enquiry.getDestination());
    details.put("polCode", enquiry.getPolCode());
    details.put("podCode", enquiry.getPodCode());
    details.put("fpodCode", enquiry.getFpodCode());
    details.put("portOfLoading", enquiry.getPortOfLoading());
    details.put("portOfDischarge", enquiry.getPortOfDischarge());
    details.put("airportOfOrigin", enquiry.getAirportOfOrigin());
    details.put("airportOfDestination", enquiry.getAirportOfDestination());
    details.put("incoterm", enquiry.getIncoterm());
    details.put("cargoType", enquiry.getCargoType());
    details.put("commodity", enquiry.getCommodity());
    details.put("grossWeight", enquiry.getGrossWeight());
    details.put("weightUnit", enquiry.getWeightUnit());
    details.put("volume", enquiry.getVolume());
    details.put("volumeUnit", enquiry.getVolumeUnit());
    details.put("packageCount", enquiry.getPackageCount());
    details.put(
        "readyDate", enquiry.getReadyDate() == null ? null : enquiry.getReadyDate().toString());
    details.put("containerType", enquiry.getContainerType());
    details.put("containerQuantity", enquiry.getContainerQuantity());
    details.put("cbm", enquiry.getVolume());
    details.put("weightKg", enquiry.getGrossWeight());
    List<String> defaultScope =
        enquiry.getIncoterm() != null && IncotermScopeResolver.supported(enquiry.getIncoterm())
            ? IncotermScopeResolver.defaults(
                enquiry.getIncoterm(),
                defaultShipmentMode(enquiry.getMode(), enquiry.getCargoType()),
                enquiry.getFpodCode() != null)
            : List.of();
    List<String> scope =
        enquiry.getServiceScope() == null ? defaultScope : enquiry.getServiceScope();
    details.put("scope", scope);
    details.put(
        "scopeCustomized",
        !new java.util.HashSet<>(scope).equals(new java.util.HashSet<>(defaultScope)));
    details.put("quoteCurrency", enquiry.getQuoteCurrency());
    details.put("referenceDestinationCharges", enquiry.isReferenceDestinationCharges());
    details.put("pickupRequired", enquiry.isPickupRequired());
    details.put("exportCustomsRequired", enquiry.isExportCustomsRequired());
    details.put("insuranceRequired", enquiry.isInsuranceRequired());
    details.put("destinationDeliveryRequired", enquiry.isDestinationDeliveryRequired());
    try {
      quotation.setShipmentSnapshot(mapper.writeValueAsString(details));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not snapshot quotation shipment", exception);
    }
    quotation.setSourceMessageId(enquiry.getSourceMessageId());
    quotation.setSourceThreadId(enquiry.getSourceThreadId());
    quotation.setSourceConnectionId(enquiry.getSourceConnectionId());
  }

  public void applyOverride(
      Quotation quotation, ShipmentOverrideInput override, LocationSearchService locations) {
    if (override == null) return;
    try {
      ObjectNode details = (ObjectNode) mapper.readTree(quotation.getShipmentSnapshot());
      String previousIncoterm = details.path("incoterm").asText();
      if (override.shipmentMode() != null) {
        String shipmentMode = override.shipmentMode().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("FCL", "LCL", "AIR", "FTL", "LTL").contains(shipmentMode))
          throw new ApiException(400, "INVALID_MODE", "Select a supported shipment mode");
        details.put("shipmentMode", shipmentMode);
        details.put("mode", transportMode(shipmentMode));
        if ("AIR".equals(shipmentMode)) details.putNull("cargoType");
        else details.put("cargoType", shipmentMode);
      }
      if (override.incoterm() != null) {
        String incoterm = override.incoterm().toUpperCase(java.util.Locale.ROOT);
        if (incoterm.isBlank()) {
          details.putNull("incoterm");
        } else if (!Set.of("EXW", "FCA", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP")
            .contains(incoterm)) {
          throw new ApiException(400, "INVALID_INCOTERM", "Select a supported Incoterm");
        } else {
          details.put("incoterm", incoterm);
        }
      }
      String mode = details.path("mode").asText();
      if (override.polCode() != null || override.podCode() != null) {
        if (override.polCode() == null || override.podCode() == null)
          throw new ApiException(400, "LOCATION_REQUIRED", "Select both quotation route locations");
        var pol = locations.require(override.polCode(), mode);
        var pod = locations.require(override.podCode(), mode);
        details.put("polCode", pol.getCode());
        details.put("podCode", pod.getCode());
        details.put("origin", pol.getName());
        details.put("destination", pod.getName());
        if ("OCEAN".equals(mode)) {
          details.put("portOfLoading", pol.getName());
          details.put("portOfDischarge", pod.getName());
        } else if ("AIR".equals(mode)) {
          details.put("airportOfOrigin", pol.getName());
          details.put("airportOfDestination", pod.getName());
        }
        if (override.fpodCode() == null || override.fpodCode().isBlank()) {
          details.putNull("fpodCode");
        } else {
          details.put("fpodCode", locations.require(override.fpodCode(), "ALL").getCode());
        }
      }
      put(details, "customerReference", override.customerReference());
      put(details, "cargoReadyDate", override.cargoReadyDate());
      put(details, "commodity", override.commodity());
      put(details, "dangerousGoods", override.dangerousGoods());
      put(details, "freeDaysAtDestination", override.freeDaysAtDestination());
      put(details, "containerType", override.containerType());
      put(details, "containerQuantity", override.containerQuantity());
      put(details, "grossWeightPerContainerKg", override.grossWeightPerContainerKg());
      put(details, "packageCount", override.packageCount());
      put(details, "cbm", override.cbm());
      put(details, "weightKg", override.weightKg());
      put(details, "stackable", override.stackable());
      put(details, "pieces", override.pieces());
      put(details, "chargeableWeightKg", override.chargeableWeightKg());
      put(details, "truckType", override.truckType());
      put(details, "truckCount", override.truckCount());
      String shipmentMode = details.path("shipmentMode").asText();
      if ("LCL".equals(shipmentMode)
          && details.path("cbm").isNumber()
          && details.path("weightKg").isNumber())
        details.put(
            "weightMeasure",
            Math.max(details.path("cbm").asDouble(), details.path("weightKg").asDouble() / 1000));
      if (override.scope() != null) {
        IncotermScopeResolver.validateScope(
            override.scope(), shipmentMode, !details.path("fpodCode").asText().isBlank());
        details.set("scope", mapper.valueToTree(override.scope()));
        details.put(
            "scopeCustomized",
            !IncotermScopeResolver.supported(details.path("incoterm").asText())
                || !new java.util.HashSet<>(override.scope())
                    .equals(
                        new java.util.HashSet<>(
                            IncotermScopeResolver.defaults(
                                details.path("incoterm").asText(),
                                shipmentMode,
                                !details.path("fpodCode").asText().isBlank()))));
      } else if ((!details.has("scope")
              || details.path("scope").isNull()
              || !previousIncoterm.equals(details.path("incoterm").asText()))
          && !details.path("scopeCustomized").asBoolean(false)) {
        if (IncotermScopeResolver.supported(details.path("incoterm").asText()))
          details.set(
              "scope",
              mapper.valueToTree(
                  IncotermScopeResolver.defaults(
                      details.path("incoterm").asText(),
                      shipmentMode,
                      !details.path("fpodCode").asText().isBlank())));
      }
      quotation.setShipmentSnapshot(mapper.writeValueAsString(details));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not update quotation shipment", exception);
    }
  }

  private static String defaultShipmentMode(String mode, String cargoType) {
    if ("AIR".equals(mode)) return "AIR";
    if (IncotermScopeResolver.isRoad(mode)) return "LTL".equals(cargoType) ? "LTL" : "FTL";
    return "LCL".equals(cargoType) ? "LCL" : "FCL";
  }

  private static String transportMode(String shipmentMode) {
    return switch (shipmentMode) {
      case "FCL", "LCL" -> "OCEAN";
      case "FTL", "LTL" -> "ROAD";
      default -> "AIR";
    };
  }

  private static void put(ObjectNode node, String field, Object value) {
    if (value == null) return;
    if (value instanceof String text) node.put(field, text);
    else if (value instanceof Integer number) node.put(field, number);
    else if (value instanceof BigDecimal number) node.put(field, number);
    else if (value instanceof Boolean flag) node.put(field, flag);
    else node.put(field, value.toString());
  }

  public void validate(Quotation quotation) {
    try {
      var details = mapper.readTree(quotation.getShipmentSnapshot());
      String mode = details.path("shipmentMode").asText();
      if (!Set.of("FCL", "LCL", "AIR", "FTL", "LTL").contains(mode))
        throw new ApiException(400, "INVALID_MODE", "Select a supported shipment mode");
      if (!IncotermScopeResolver.supported(details.path("incoterm").asText()))
        throw new ApiException(400, "INVALID_INCOTERM", "Select a supported Incoterm");
      if (details.path("polCode").asText().isBlank() || details.path("podCode").asText().isBlank())
        throw new ApiException(400, "LOCATION_REQUIRED", "Select POL and POD");
      if ("FCL".equals(mode)
          && (details.path("containerType").asText().isBlank()
              || details.path("containerQuantity").asInt() <= 0))
        throw new ApiException(400, "SHIPMENT_REQUIRED", "Select container type and quantity");
      if ("FTL".equals(mode)
          && (details.path("truckType").asText().isBlank()
              || details.path("truckCount").asInt() <= 0))
        throw new ApiException(400, "SHIPMENT_REQUIRED", "Select truck type and quantity");
      if (details.path("freeDaysAtDestination").isNumber()
          && details.path("freeDaysAtDestination").asInt() < 0)
        throw new ApiException(400, "SHIPMENT_INVALID", "Free days cannot be negative");
      if (details.path("scope").isArray()) {
        List<String> scope = new java.util.ArrayList<>();
        details.path("scope").forEach(value -> scope.add(value.asText()));
        IncotermScopeResolver.validateScope(
            scope, mode, !details.path("fpodCode").asText().isBlank());
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not validate quotation shipment", exception);
    }
  }

  public boolean referenceChargeAllowed(Quotation quotation, String description) {
    try {
      var details = mapper.readTree(quotation.getShipmentSnapshot());
      return IncotermScopeResolver.DESTINATION.equals(description)
          && IncotermScopeResolver.referenceDestinationAllowed(details.path("incoterm").asText());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not validate reference charge", exception);
    }
  }

  public boolean chargeApplicable(Quotation quotation, String description) {
    try {
      var details = mapper.readTree(quotation.getShipmentSnapshot());
      String mode = details.path("shipmentMode").asText();
      if (IncotermScopeResolver.HAULAGE.equals(description))
        return !IncotermScopeResolver.isRoad(mode) && !details.path("fpodCode").asText().isBlank();
      if (IncotermScopeResolver.isRoad(mode))
        return !Set.of(
                IncotermScopeResolver.ORIGIN,
                IncotermScopeResolver.DESTINATION,
                "Ocean Freight",
                "Air freight")
            .contains(description);
      return true;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not validate quotation charge", exception);
    }
  }

  public void requireCanonicalRoute(Quotation quotation) {
    try {
      var details = mapper.readTree(quotation.getShipmentSnapshot());
      String mode = details.path("mode").asText();
      if (("OCEAN".equals(mode) || "AIR".equals(mode))
          && (details.path("polCode").isNull()
              || details.path("polCode").asText().isBlank()
              || details.path("podCode").isNull()
              || details.path("podCode").asText().isBlank()))
        throw new ApiException(
            400, "LOCATION_REQUIRED", "Select canonical quotation route locations");
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not validate quotation shipment", exception);
    }
  }
}
