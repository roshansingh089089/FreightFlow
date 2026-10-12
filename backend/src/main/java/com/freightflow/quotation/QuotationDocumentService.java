package com.freightflow.quotation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.entity.CustomerAddress;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.repository.AddressRepo;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.entity.QuotationLine;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.reference.LocationSearchService;
import com.freightflow.tenant.repository.TenantRepo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class QuotationDocumentService {
  public record Charge(
      String description,
      String scope,
      BigDecimal quantity,
      String basis,
      String originalCurrency,
      BigDecimal originalUnitPrice,
      BigDecimal convertedUnitPrice,
      BigDecimal convertedAmount,
      BigDecimal fxRate,
      BigDecimal fxReferenceRate,
      String fxSource,
      LocalDate fxObservationDate,
      BigDecimal fxBufferPercent) {}

  public record Document(
      String number,
      String status,
      String companyName,
      String companyCountry,
      String customerName,
      String contactName,
      String contactPosition,
      String contactEmail,
      String customerAddress,
      String preparedBy,
      String preparedByEmail,
      LocalDate issueDate,
      LocalDate validUntil,
      String customerReference,
      String mode,
      String origin,
      String destination,
      String finalDestination,
      String cargo,
      String incoterm,
      String transitTime,
      String currency,
      List<Charge> charges,
      BigDecimal total,
      List<String> included,
      List<String> excluded,
      String terms) {}

  private final QuotationLookupService lookup;
  private final LineRepo lines;
  private final ContactRepo contacts;
  private final AddressRepo addresses;
  private final TenantRepo tenants;
  private final UserRepo users;
  private final ObjectMapper mapper;
  private final LocationSearchService locations;

  public QuotationDocumentService(
      QuotationLookupService lookup,
      LineRepo lines,
      ContactRepo contacts,
      AddressRepo addresses,
      TenantRepo tenants,
      UserRepo users,
      ObjectMapper mapper,
      LocationSearchService locations) {
    this.lookup = lookup;
    this.lines = lines;
    this.contacts = contacts;
    this.addresses = addresses;
    this.tenants = tenants;
    this.users = users;
    this.mapper = mapper;
    this.locations = locations;
  }

  public Document document(Quotation quotation) {
    if (quotation.getDocumentSnapshot() != null) {
      try {
        ObjectNode snapshot = (ObjectNode) mapper.readTree(quotation.getDocumentSnapshot());
        snapshot.put("status", quotation.getStatus());
        if (!snapshot.hasNonNull("finalDestination") && quotation.getShipmentSnapshot() != null)
          snapshot.put(
              "finalDestination",
              finalDestination(mapper.readTree(quotation.getShipmentSnapshot())));
        return mapper.treeToValue(snapshot, Document.class);
      } catch (JsonProcessingException exception) {
        throw new ApiException(
            500, "QUOTE_DOCUMENT_INVALID", "Quotation document snapshot is invalid");
      }
    }
    return build(quotation);
  }

  public void freeze(Quotation quotation) {
    try {
      quotation.setDocumentSnapshot(mapper.writeValueAsString(build(quotation)));
    } catch (JsonProcessingException exception) {
      throw new ApiException(500, "QUOTE_DOCUMENT_INVALID", "Could not save quotation document");
    }
  }

  private Document build(Quotation quotation) {
    var customer = lookup.customer(quotation.getCustomerId());
    var tenant = tenants.findById(quotation.getTenantId()).orElseThrow();
    CustomerContact contact =
        contacts
            .findFirstByTenantIdAndCustomerIdAndPrimaryTrue(
                quotation.getTenantId(), customer.getId())
            .orElse(null);
    CustomerAddress address =
        addresses.findByTenantIdAndCustomerId(quotation.getTenantId(), customer.getId()).stream()
            .filter(a -> "BUSINESS".equals(a.getAddressType()))
            .findFirst()
            .orElse(null);
    AppUser author =
        users
            .findById(quotation.getCreatedBy())
            .filter(u -> quotation.getTenantId().equals(u.getTenantId()))
            .orElse(null);
    JsonNode shipment;
    try {
      shipment =
          quotation.getShipmentSnapshot() == null
              ? mapper.valueToTree(lookup.enquiry(quotation.getEnquiryId()))
              : mapper.readTree(quotation.getShipmentSnapshot());
    } catch (JsonProcessingException exception) {
      throw new ApiException(
          500, "QUOTE_SNAPSHOT_INVALID", "Quotation shipment snapshot is invalid");
    }
    List<Charge> charges = new ArrayList<>();
    Map<String, Boolean> scopes = new LinkedHashMap<>();
    for (QuotationLine line :
        lines.findByTenantIdAndQuotationIdOrderBySortOrder(
            quotation.getTenantId(), quotation.getId())) {
      String scope = scope(line.getDescription());
      scopes.put(scope, true);
      charges.add(
          new Charge(
              "FOR_REFERENCE".equals(line.getPricingTreatment())
                  ? line.getDescription() + " (For reference)"
                  : line.getDescription(),
              scope,
              line.getQuantity(),
              null,
              Objects.toString(line.getOriginalCurrency(), quotation.getCurrency()),
              line.getOriginalUnitPrice() == null
                  ? line.getUnitPrice()
                  : line.getOriginalUnitPrice(),
              line.getUnitPrice(),
              line.getAmount(),
              line.getFxRate(),
              line.getFxReferenceRate(),
              line.getFxSource(),
              line.getFxObservationDate(),
              line.getFxBufferPercent()));
    }
    List<String> excluded = List.of();
    String addressText =
        address == null
            ? null
            : String.join(
                ", ",
                java.util.stream.Stream.of(
                        address.getLine1(),
                        address.getLine2(),
                        address.getCity(),
                        address.getState(),
                        address.getPostalCode(),
                        address.getCountry())
                    .filter(s -> s != null && !s.isBlank())
                    .toList());
    String cargo = text(shipment, "commodity");
    String equipment = text(shipment, "containerType");
    String count = text(shipment, "containerQuantity");
    if (equipment != null && count != null)
      cargo = Objects.toString(cargo, "Cargo") + " · " + count + " × " + equipment;
    return new Document(
        quotation.getQuotationNumber(),
        quotation.getStatus(),
        tenant.getCompanyName(),
        tenant.getCountry(),
        customer.getCompanyName(),
        contact == null ? null : contact.getName(),
        contact == null ? null : contact.getDesignation(),
        contact == null ? text(shipment, "contactEmail") : contact.getEmail(),
        addressText,
        author == null ? null : author.getFullName(),
        author == null ? null : author.getEmail(),
        quotation.getCreatedAt() == null
            ? null
            : LocalDate.ofInstant(quotation.getCreatedAt(), ZoneOffset.UTC),
        quotation.getValidUntil(),
        null,
        text(shipment, "mode"),
        text(shipment, "origin"),
        text(shipment, "destination"),
        finalDestination(shipment),
        cargo,
        text(shipment, "incoterm"),
        quotation.getTransitTimeText(),
        quotation.getCurrency(),
        charges,
        quotation.getTotalAmount(),
        new ArrayList<>(scopes.keySet()),
        excluded,
        quotation.getTermsAndConditions());
  }

  private String finalDestination(JsonNode shipment) {
    String fpodCode = text(shipment, "fpodCode");
    String podCode = text(shipment, "podCode");
    String finalDestination = null;
    if (fpodCode != null && !fpodCode.equalsIgnoreCase(Objects.toString(podCode, ""))) {
      finalDestination = text(shipment, "fpodName");
      if (finalDestination == null) {
        try {
          finalDestination = locations.require(fpodCode, "ALL").getName();
        } catch (ApiException exception) {
          finalDestination = fpodCode;
        }
      }
    }
    return finalDestination;
  }

  private static String text(JsonNode node, String key) {
    String value = node.path(key).asText("");
    return value.isBlank() ? null : value;
  }

  static String scope(String description) {
    String value = Objects.toString(description, "").toLowerCase();
    if (value.contains("pickup") || value.contains("trucking")) return "Pickup & trucking";
    if (value.contains("export customs")) return "Export customs clearance";
    if (value.contains("origin") || value.contains("terminal") || value.contains("seal fee"))
      return "Origin charges";
    if (value.contains("air freight")) return "Air freight";
    if (value.contains("road freight")) return "Road freight";
    if (value.contains("ocean") || value.contains("freight")) return "Ocean freight";
    if (value.contains("destination")) return "Destination charges";
    if (value.contains("import customs")) return "Import customs clearance";
    if (value.contains("door delivery")) return "Door delivery";
    if (value.contains("duties") || value.contains("tax")) return "Duties & taxes";
    if (value.contains("insurance")) return "Cargo insurance";
    return "Other services";
  }
}
