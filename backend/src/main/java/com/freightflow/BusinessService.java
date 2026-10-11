package com.freightflow;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.CustomerProfileController;
import com.freightflow.customer.CustomerProfileService;
import com.freightflow.customer.dto.request.AddressInput;
import com.freightflow.customer.dto.request.ContactInput;
import com.freightflow.customer.dto.request.CustomerInput;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.entity.CustomerAddress;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.repository.AddressRepo;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.enquiry.dto.request.EnquiryInput;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.enquiry.repository.EnquiryRepo;
import com.freightflow.fx.CurrencyConversionService;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.quotation.IncotermScopeResolver;
import com.freightflow.quotation.QuotationShipmentMapper;
import com.freightflow.quotation.dto.request.LineInput;
import com.freightflow.quotation.dto.request.QuoteInput;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.entity.QuotationLine;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.reference.LocationSearchService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class BusinessService {
  final JdbcTemplate jdbc;
  final CustomerRepo customers;
  final ContactRepo contacts;
  final AddressRepo addresses;
  final EnquiryRepo enquiries;
  final QuoteRepo quotes;
  final LineRepo lines;
  final CustomerProfileService profiles;
  final EmailMessageRepo sourceMessages;
  final MailConnectionRepo mailConnections;
  final LocationSearchService locations;
  final QuotationShipmentMapper shipmentMapper;
  final CurrencyConversionService conversion;

  BusinessService(
      JdbcTemplate j,
      CustomerRepo c,
      ContactRepo ct,
      AddressRepo a,
      EnquiryRepo e,
      QuoteRepo q,
      LineRepo l,
      CustomerProfileService profiles,
      EmailMessageRepo sourceMessages,
      MailConnectionRepo mailConnections,
      LocationSearchService locations,
      QuotationShipmentMapper shipmentMapper,
      CurrencyConversionService conversion) {
    jdbc = j;
    customers = c;
    contacts = ct;
    addresses = a;
    enquiries = e;
    quotes = q;
    lines = l;
    this.profiles = profiles;
    this.sourceMessages = sourceMessages;
    this.mailConnections = mailConnections;
    this.locations = locations;
    this.shipmentMapper = shipmentMapper;
    this.conversion = conversion;
  }

  UUID tenant() {
    return SecurityConfig.current().getTenantId();
  }

  UUID user() {
    return SecurityConfig.current().getId();
  }

  public Customer customer(UUID id) {
    return customers
        .findByIdAndTenantId(id, tenant())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Customer not found"));
  }

  public Enquiry enquiry(UUID id) {
    return enquiries
        .findByIdAndTenantId(id, tenant())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Enquiry not found"));
  }

  public Quotation quote(UUID id) {
    return quotes
        .findByIdAndTenantId(id, tenant())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
  }

  @Transactional
  public Object createCustomer(@Valid CustomerInput r) {
    profiles.assertUniqueCompany(r.companyName(), null);
    Customer c = new Customer();
    c.setTenantId(tenant());
    c.setSalesOwnerUserId(r.salesOwnerUserId() == null ? user() : r.salesOwnerUserId());
    c.setCustomerCode(
        "CUS-"
            + String.format(
                "%06d", jdbc.queryForObject("select nextval('customer_code_seq')", Long.class)));
    apply(c, r);
    customers.save(c);
    saveContact(c, r.primaryContact());
    saveAddresses(c, r.addresses());
    profiles.audit("CREATED", c.getId(), null);
    return customerDetail(c);
  }

  public Object customers(
      String search,
      String type,
      String country,
      String status,
      boolean mine,
      String sort,
      String direction,
      int page,
      int size) {
    if (!List.of("createdAt", "companyName", "updatedAt").contains(sort))
      throw new ApiException(400, "SORT_INVALID", "Unsupported customer sort");
    Sort.Direction order =
        "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return customers
        .search(
            tenant(),
            search,
            type,
            country,
            status,
            mine ? user() : null,
            PageRequest.of(
                Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(order, sort)))
        .map(this::customerSummary);
  }

  public Object getCustomer(UUID id) {
    return customerDetail(customer(id));
  }

  @Transactional
  public Object updateCustomer(UUID id, @Valid CustomerInput r) {
    Customer c = customer(id);
    profiles.assertUniqueCompany(r.companyName(), id);
    apply(c, r);
    customers.save(c);
    CustomerContact ct =
        contacts
            .findFirstByTenantIdAndCustomerIdAndPrimaryTrue(tenant(), id)
            .orElseGet(
                () -> {
                  CustomerContact x = new CustomerContact();
                  x.setTenantId(tenant());
                  x.setCustomerId(id);
                  x.setPrimary(true);
                  return x;
                });
    ct.setName(r.primaryContact().name());
    ct.setEmail(r.primaryContact().email());
    ct.setPhone(r.primaryContact().phone());
    ct.setDesignation(r.primaryContact().designation());
    contacts.save(ct);
    profiles.audit("UPDATED", id, null);
    return customerDetail(c);
  }

  @Transactional
  public Object archiveCustomer(UUID id) {
    Customer c = customer(id);
    if (c.getArchivedAt() == null) {
      c.setArchivedAt(Instant.now());
      c.setStatus("INACTIVE");
      customers.save(c);
      profiles.audit("ARCHIVED", id, null);
    }
    return customerDetail(c);
  }

  @Transactional
  public Object restoreCustomer(UUID id) {
    Customer c = customer(id);
    if (c.getArchivedAt() != null) {
      c.setArchivedAt(null);
      c.setStatus("ACTIVE");
      customers.save(c);
      profiles.audit("RESTORED", id, null);
    }
    return customerDetail(c);
  }

  void apply(Customer c, CustomerInput r) {
    if (!List.of("EXPORTER", "IMPORTER", "FREIGHT_PARTNER", "OTHER").contains(r.customerType()))
      throw new ApiException(400, "VALIDATION_ERROR", "Invalid customer type");
    c.setCompanyName(r.companyName().trim());
    c.setCustomerType(r.customerType());
    c.setCountry(r.country());
    c.setGstin(r.gstin());
    c.setIec(r.iec());
    if (r.salesOwnerUserId() != null) {
      profiles.assertOwner(r.salesOwnerUserId());
      c.setSalesOwnerUserId(r.salesOwnerUserId());
    }
    if (r.creditTermsDays() != null) c.setCreditTermsDays(r.creditTermsDays());
    if (r.creditLimit() != null) c.setCreditLimit(r.creditLimit());
    if (r.status() != null && !List.of("ACTIVE", "INACTIVE").contains(r.status()))
      throw new ApiException(400, "VALIDATION_ERROR", "Invalid customer status");
    if (r.status() != null) c.setStatus(r.status());
  }

  void saveContact(Customer c, ContactInput r) {
    CustomerContact x = new CustomerContact();
    x.setTenantId(tenant());
    x.setCustomerId(c.getId());
    x.setPrimary(true);
    x.setName(r.name());
    x.setEmail(r.email());
    x.setPhone(r.phone());
    x.setDesignation(r.designation());
    contacts.save(x);
  }

  void saveAddresses(Customer c, List<AddressInput> xs) {
    if (xs == null) return;
    for (AddressInput r : xs) {
      CustomerAddress a = new CustomerAddress();
      a.setTenantId(tenant());
      a.setCustomerId(c.getId());
      a.setAddressType(r.addressType());
      a.setLine1(r.line1());
      a.setLine2(r.line2());
      a.setCity(r.city());
      a.setState(r.state());
      a.setPostalCode(r.postalCode());
      a.setCountry(r.country());
      addresses.save(a);
    }
  }

  Map<String, Object> customerSummary(Customer c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("customerCode", c.getCustomerCode());
    m.put("companyName", c.getCompanyName());
    m.put("customerType", c.getCustomerType());
    m.put("country", c.getCountry());
    m.put("status", c.getStatus());
    m.put("salesOwnerUserId", c.getSalesOwnerUserId());
    m.put("creditTermsDays", c.getCreditTermsDays());
    m.put("creditLimit", c.getCreditLimit());
    m.put("archivedAt", c.getArchivedAt());
    m.put("createdAt", c.getCreatedAt());
    m.put(
        "primaryContact",
        contacts
            .findFirstByTenantIdAndCustomerIdAndPrimaryTrue(tenant(), c.getId())
            .map(CustomerProfileController::view)
            .orElse(null));
    return m;
  }

  Object customerDetail(Customer c) {
    Map<String, Object> m = new LinkedHashMap<>(customerSummary(c));
    m.put("gstin", c.getGstin());
    m.put("iec", c.getIec());
    m.put(
        "contacts",
        contacts.findByTenantIdAndCustomerIdOrderByPrimaryDesc(tenant(), c.getId()).stream()
            .map(CustomerProfileController::view)
            .toList());
    m.put(
        "addresses",
        addresses.findByTenantIdAndCustomerId(tenant(), c.getId()).stream()
            .map(CustomerProfileController::view)
            .toList());
    m.put("extraCc", profiles.cc(c.getId()).stream().map(CustomerProfileController::view).toList());
    m.put(
        "recentEnquiries",
        enquiries.findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenant(), c.getId()).stream()
            .map(this::enquirySummary)
            .toList());
    m.put(
        "recentQuotations",
        quotes.findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenant(), c.getId()).stream()
            .map(this::quoteSummary)
            .toList());
    return m;
  }

  @Transactional
  public Object createEnquiry(@Valid EnquiryInput r) {
    if (r.sourceMessageId() != null) {
      var existing = enquiries.findByTenantIdAndSourceMessageId(tenant(), r.sourceMessageId());
      if (existing.isPresent()) return enquiryDetail(existing.get());
    }
    Enquiry e = new Enquiry();
    e.setTenantId(tenant());
    e.setCreatedBy(user());
    e.setEnquiryCode(
        "ENQ-"
            + Year.now().getValue()
            + "-"
            + String.format(
                "%06d", jdbc.queryForObject("select nextval('enquiry_code_seq')", Long.class)));
    apply(e, r, true);
    enquiries.save(e);
    return enquiryDetail(e);
  }

  public Object enquiries(
      String search,
      UUID customerId,
      String mode,
      String status,
      LocalDate from,
      int page,
      int size) {
    return enquiries
        .search(
            tenant(),
            search,
            customerId,
            mode,
            status,
            from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::enquirySummary);
  }

  public Object getEnquiry(UUID id) {
    return enquiryDetail(enquiry(id));
  }

  @Transactional
  public Object updateEnquiry(UUID id, @Valid EnquiryInput r) {
    Enquiry e = enquiry(id);
    apply(e, r, false);
    enquiries.save(e);
    return enquiryDetail(e);
  }

  void apply(Enquiry e, EnquiryInput r) {
    apply(e, r, false);
  }

  void apply(Enquiry e, EnquiryInput r, boolean creating) {
    customer(r.customerId());
    if (r.sourceMessageId() != null && !r.sourceMessageId().equals(e.getSourceMessageId())) {
      var connection =
          mailConnections
              .findByTenantIdAndUserIdAndActiveTrue(tenant(), user())
              .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Source email not found"));
      var source =
          sourceMessages
              .findByIdAndTenantIdAndConnectionId(r.sourceMessageId(), tenant(), connection.getId())
              .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Source email not found"));
      e.setSourceMessageId(source.getId());
      e.setSourceThreadId(source.getThreadId());
      e.setSourceConnectionId(connection.getId());
    } else if (e.getSourceMessageId() == null) {
      e.setSourceMessageId(null);
      e.setSourceThreadId(null);
      e.setSourceConnectionId(null);
    }
    if (!List.of("OCEAN", "AIR", "ROAD").contains(r.mode()))
      throw new ApiException(400, "VALIDATION_ERROR", "Invalid mode");
    if (r.contactId() != null
        && contacts.findByTenantIdAndCustomerIdOrderByPrimaryDesc(tenant(), r.customerId()).stream()
            .noneMatch(c -> c.getId().equals(r.contactId())))
      throw new ApiException(400, "VALIDATION_ERROR", "Contact does not belong to customer");
    boolean routeUnchanged =
        !creating
            && java.util.Objects.equals(e.getMode(), r.mode())
            && java.util.Objects.equals(e.getOrigin(), r.origin())
            && java.util.Objects.equals(e.getDestination(), r.destination())
            && java.util.Objects.equals(e.getPortOfLoading(), r.portOfLoading())
            && java.util.Objects.equals(e.getPortOfDischarge(), r.portOfDischarge())
            && java.util.Objects.equals(e.getAirportOfOrigin(), r.airportOfOrigin())
            && java.util.Objects.equals(e.getAirportOfDestination(), r.airportOfDestination());
    e.setCustomerId(r.customerId());
    e.setContactId(r.contactId());
    e.setContactEmail(
        r.contactEmail() == null || r.contactEmail().isBlank() ? null : r.contactEmail().trim());
    e.setMode(r.mode());
    e.setOrigin(r.origin());
    e.setDestination(r.destination());
    e.setPortOfLoading(r.portOfLoading());
    e.setPortOfDischarge(r.portOfDischarge());
    if ("OCEAN".equals(r.mode()) || "AIR".equals(r.mode())) {
      if (routeUnchanged && r.polCode() == null && r.podCode() == null) {
        // Historical text-only routes can still be edited without a destructive backfill.
      } else {
        e.setPolCode(locations.require(r.polCode(), r.mode()).getCode());
        e.setPodCode(locations.require(r.podCode(), r.mode()).getCode());
      }
    } else {
      if (!routeUnchanged || r.polCode() != null)
        e.setPolCode(r.polCode() == null ? null : locations.require(r.polCode(), "ALL").getCode());
      if (!routeUnchanged || r.podCode() != null)
        e.setPodCode(r.podCode() == null ? null : locations.require(r.podCode(), "ALL").getCode());
    }
    if (!routeUnchanged || r.fpodCode() != null)
      e.setFpodCode(r.fpodCode() == null ? null : locations.require(r.fpodCode(), "ALL").getCode());
    e.setAirportOfOrigin(r.airportOfOrigin());
    e.setAirportOfDestination(r.airportOfDestination());
    e.setCommodity(r.commodity());
    e.setGrossWeight(r.grossWeight());
    e.setWeightUnit(r.weightUnit());
    e.setVolume(r.volume());
    e.setVolumeUnit(r.volumeUnit());
    e.setPackageCount(r.packageCount());
    e.setReadyDate(r.readyDate());
    e.setIncoterm(r.incoterm());
    e.setCargoType(r.cargoType());
    e.setContainerType(r.containerType());
    e.setContainerQuantity(r.containerQuantity());
    e.setPickupRequired(r.pickupRequired());
    e.setExportCustomsRequired(r.exportCustomsRequired());
    e.setInsuranceRequired(r.insuranceRequired());
    e.setDestinationDeliveryRequired(r.destinationDeliveryRequired());
    if (r.serviceScope() != null) {
      String shipmentMode =
          "AIR".equals(r.mode())
              ? "AIR"
              : "ROAD".equals(r.mode()) ? "ROAD" : "LCL".equals(r.cargoType()) ? "LCL" : "FCL";
      IncotermScopeResolver.validateScope(r.serviceScope(), shipmentMode, e.getFpodCode() != null);
      if (IncotermScopeResolver.supported(r.incoterm())
          && !r.serviceScope().contains(IncotermScopeResolver.freight(shipmentMode)))
        throw new ApiException(400, "INVALID_SCOPE", "Freight must be included in scope");
      if (IncotermScopeResolver.referenceDestinationAllowed(r.incoterm())
          && r.serviceScope().contains(IncotermScopeResolver.DESTINATION))
        throw new ApiException(
            400, "INVALID_SCOPE", "Destination charges are for reference under this Incoterm");
      e.setServiceScope(List.copyOf(new java.util.LinkedHashSet<>(r.serviceScope())));
      e.setPickupRequired(r.serviceScope().contains(IncotermScopeResolver.PICKUP));
      e.setExportCustomsRequired(r.serviceScope().contains(IncotermScopeResolver.EXPORT));
      e.setInsuranceRequired(r.serviceScope().contains(IncotermScopeResolver.INSURANCE));
      e.setDestinationDeliveryRequired(r.serviceScope().contains(IncotermScopeResolver.DELIVERY));
    } else if (creating) {
      e.setServiceScope(null);
    }
    if (r.quoteCurrency() != null && !r.quoteCurrency().isBlank()) {
      String currency = r.quoteCurrency().trim().toUpperCase(java.util.Locale.ROOT);
      if (!Set.of("USD", "THB", "INR", "AED", "MYR", "VND", "IDR", "SGD", "EUR").contains(currency))
        throw new ApiException(400, "INVALID_CURRENCY", "Select a supported quote currency");
      e.setQuoteCurrency(currency);
    } else if (creating) {
      e.setQuoteCurrency("USD");
    }
    if (r.referenceDestinationCharges()
        && ("ROAD".equals(r.mode())
            || !IncotermScopeResolver.referenceDestinationAllowed(r.incoterm())))
      throw new ApiException(
          400, "INVALID_REFERENCE_CHARGE", "Destination reference charges require a C term");
    if (r.referenceDestinationCharges()
        && r.serviceScope() != null
        && r.serviceScope().contains(IncotermScopeResolver.DESTINATION))
      throw new ApiException(
          400,
          "INVALID_REFERENCE_CHARGE",
          "Destination charges cannot be both included and for reference");
    e.setReferenceDestinationCharges(r.referenceDestinationCharges());
    e.setNotes(r.notes());
    if (r.status() != null) {
      if (!List.of("DRAFT", "NEW", "PRICING_PENDING", "QUOTED", "CANCELLED").contains(r.status()))
        throw new ApiException(400, "VALIDATION_ERROR", "Invalid enquiry status");
      e.setStatus(r.status());
    }
  }

  Map<String, Object> enquirySummary(Enquiry e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("enquiryCode", e.getEnquiryCode());
    m.put("customerId", e.getCustomerId());
    m.put("customerName", customer(e.getCustomerId()).getCompanyName());
    m.put("mode", e.getMode());
    m.put("origin", e.getOrigin());
    m.put("destination", e.getDestination());
    m.put("commodity", e.getCommodity());
    m.put("status", e.getStatus());
    m.put("createdAt", e.getCreatedAt());
    return m;
  }

  Object enquiryDetail(Enquiry e) {
    Map<String, Object> m = new LinkedHashMap<>(enquirySummary(e));
    m.put("enquiry", e);
    m.put("customer", customerSummary(customer(e.getCustomerId())));
    return m;
  }

  @Transactional
  public Object createQuote(UUID id, @Valid QuoteInput r) {
    Enquiry e = enquiry(id);
    Quotation q = new Quotation();
    q.setTenantId(tenant());
    q.setCreatedBy(user());
    q.setEnquiryId(e.getId());
    q.setCustomerId(e.getCustomerId());
    shipmentMapper.snapshot(e, q);
    q.setQuotationNumber(
        "QT-"
            + Year.now().getValue()
            + "-"
            + String.format(
                "%06d", jdbc.queryForObject("select nextval('quotation_code_seq')", Long.class)));
    apply(q, r);
    shipmentMapper.applyOverride(q, r.shipmentOverride(), locations);
    shipmentMapper.requireCanonicalRoute(q);
    shipmentMapper.validate(q);
    quotes.save(q);
    q.setTotalAmount(saveLines(q, r.lines()));
    quotes.save(q);
    e.setStatus("QUOTED");
    enquiries.save(e);
    return quoteDetail(q);
  }

  public Object quotations(
      String search, UUID customerId, String status, LocalDate from, int page, int size) {
    return quotes
        .search(
            tenant(),
            search,
            customerId,
            status,
            from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::quoteSummary);
  }

  public Object getQuote(UUID id) {
    return quoteDetail(quote(id));
  }

  @Transactional
  public Object updateQuote(UUID id, @Valid QuoteInput r) {
    Quotation q = quote(id);
    if (!q.getStatus().equals("DRAFT"))
      throw new ApiException(409, "QUOTE_LOCKED", "Only draft quotations can be edited");
    if (q.getShipmentSnapshot() == null) shipmentMapper.snapshot(enquiry(q.getEnquiryId()), q);
    apply(q, r);
    shipmentMapper.applyOverride(q, r.shipmentOverride(), locations);
    shipmentMapper.validate(q);
    quotes.save(q);
    lines.deleteByTenantIdAndQuotationId(tenant(), id);
    lines.flush();
    q.setTotalAmount(saveLines(q, r.lines()));
    quotes.save(q);
    return quoteDetail(q);
  }

  void apply(Quotation q, QuoteInput r) {
    if (r.lines() == null || r.lines().isEmpty())
      throw new ApiException(400, "VALIDATION_ERROR", "Add at least one charge");
    if (!r.currency().matches("[A-Za-z]{3}"))
      throw new ApiException(400, "VALIDATION_ERROR", "Currency must be a three-letter code");
    q.setCurrency(r.currency().toUpperCase());
    q.setTransitTimeText(r.transitTimeText());
    q.setValidUntil(r.validUntil());
    q.setTermsAndConditions(r.termsAndConditions());
    q.setNotes(r.notes());
  }

  BigDecimal saveLines(Quotation q, List<LineInput> ls) {
    int i = 0;
    BigDecimal total = BigDecimal.ZERO;
    for (LineInput r : ls) {
      if (!shipmentMapper.chargeApplicable(q, r.description()))
        throw new ApiException(
            400, "INVALID_CHARGE", "Charge does not apply to the shipment route or mode");
      boolean reference = "FOR_REFERENCE".equals(r.pricingTreatment());
      if (shipmentMapper.referenceChargeAllowed(q, r.description()) && !reference)
        throw new ApiException(
            400,
            "REFERENCE_ONLY_CHARGE",
            "Destination charges are for reference under this Incoterm");
      if (r.pricingTreatment() != null && !reference && !"INCLUDED".equals(r.pricingTreatment()))
        throw new ApiException(400, "INVALID_PRICING_TREATMENT", "Unsupported charge treatment");
      if (reference && !shipmentMapper.referenceChargeAllowed(q, r.description()))
        throw new ApiException(
            400,
            "INVALID_REFERENCE_CHARGE",
            "Only destination charges under CIF, CFR, CPT or CIP can be for reference");
      String originalCurrency = r.currency() == null ? q.getCurrency() : r.currency();
      var result = conversion.convert(r.unitPrice(), originalCurrency, q.getCurrency());
      QuotationLine l = new QuotationLine();
      l.setTenantId(tenant());
      l.setQuotationId(q.getId());
      l.setDescription(r.description());
      l.setPricingTreatment(reference ? "FOR_REFERENCE" : "INCLUDED");
      l.setQuantity(r.quantity());
      l.setUnitPrice(result.convertedAmount());
      l.setOriginalCurrency(result.originalCurrency());
      l.setOriginalUnitPrice(result.originalAmount());
      l.setFxRate(result.rate());
      l.setFxReferenceRate(result.referenceRate());
      l.setFxSource(result.source());
      l.setFxObservationDate(result.observationDate());
      l.setFxBufferPercent(result.bufferPercent());
      l.setAmount(
          r.quantity()
              .multiply(result.convertedAmount())
              .setScale(2, java.math.RoundingMode.HALF_UP));
      if (!reference) total = total.add(l.getAmount());
      l.setSortOrder(i++);
      lines.save(l);
    }
    return total;
  }

  Map<String, Object> quoteSummary(Quotation q) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", q.getId());
    m.put("quotationNumber", q.getQuotationNumber());
    m.put("customerName", customer(q.getCustomerId()).getCompanyName());
    m.put("customerId", q.getCustomerId());
    m.put("enquiryId", q.getEnquiryId());
    m.put("currency", q.getCurrency());
    m.put("totalAmount", q.getTotalAmount());
    m.put("status", q.getStatus());
    m.put("shipmentSnapshot", q.getShipmentSnapshot());
    m.put("validUntil", q.getValidUntil());
    m.put("createdAt", q.getCreatedAt());
    return m;
  }

  Object quoteDetail(Quotation q) {
    Map<String, Object> m = new LinkedHashMap<>(quoteSummary(q));
    m.put("quotation", q);
    m.put("lines", lines.findByTenantIdAndQuotationIdOrderBySortOrder(tenant(), q.getId()));
    m.put("enquiry", enquiryDetail(enquiry(q.getEnquiryId())));
    m.put("customer", customerSummary(customer(q.getCustomerId())));
    return m;
  }

  public Object dashboard() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("newEnquiries", enquiries.countByTenantIdAndStatus(tenant(), "NEW"));
    m.put("quotesPending", quotes.countByTenantIdAndStatus(tenant(), "DRAFT"));
    m.put("quotesSent", quotes.countByTenantIdAndStatus(tenant(), "SENT"));
    m.put("customers", customers.countByTenantId(tenant()));
    m.put(
        "recentEnquiries",
        enquiries.findTop5ByTenantIdOrderByCreatedAtDesc(tenant()).stream()
            .map(this::enquirySummary)
            .toList());
    m.put(
        "expiringQuotes",
        quotes
            .findTop5ByTenantIdAndStatusAndValidUntilLessThanEqual(
                tenant(), "DRAFT", LocalDate.now().plusDays(1))
            .stream()
            .map(this::quoteSummary)
            .toList());
    return m;
  }

  public Object search(String q) {
    if (q.trim().length() < 2)
      return Map.of("customers", List.of(), "enquiries", List.of(), "quotations", List.of());
    return Map.of(
        "customers",
        customers.searchTop(tenant(), q, PageRequest.of(0, 5)).stream()
            .map(this::customerSummary)
            .toList(),
        "enquiries",
        enquiries.findTop5ByTenantIdAndEnquiryCodeContainingIgnoreCase(tenant(), q).stream()
            .map(this::enquirySummary)
            .toList(),
        "quotations",
        quotes.findTop5ByTenantIdAndQuotationNumberContainingIgnoreCase(tenant(), q).stream()
            .map(this::quoteSummary)
            .toList());
  }
}
