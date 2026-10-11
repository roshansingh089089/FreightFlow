package com.freightflow.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.entity.CustomerAddress;
import com.freightflow.customer.repository.AddressRepo;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.entity.QuotationLine;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.tenant.entity.Tenant;
import com.freightflow.tenant.repository.TenantRepo;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuotationDocumentServiceTest {
  @Test
  void freezesCustomerIdentityChargesFxAndLongAddressForFinalizedPdf() throws Exception {
    var lookup = mock(QuotationLookupService.class);
    var lines = mock(LineRepo.class);
    var contacts = mock(ContactRepo.class);
    var addresses = mock(AddressRepo.class);
    var tenants = mock(TenantRepo.class);
    var users = mock(UserRepo.class);
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var service =
        new QuotationDocumentService(lookup, lines, contacts, addresses, tenants, users, mapper);
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Quotation quotation = new Quotation();
    quotation.setTenantId(tenantId);
    quotation.setCustomerId(customerId);
    quotation.setCreatedBy(authorId);
    quotation.setQuotationNumber("QT-2026-000001");
    quotation.setCurrency("USD");
    quotation.setStatus("READY_TO_SEND");
    quotation.setTotalAmount(new BigDecimal("148.00"));
    quotation.setValidUntil(LocalDate.of(2026, 10, 31));
    quotation.setTermsAndConditions("Rates subject to availability.");
    quotation.setShipmentSnapshot(
        "{\"mode\":\"OCEAN\",\"origin\":\"Bangkok\",\"destination\":\"Nhava Sheva\",\"commodity\":\"Machinery\",\"incoterm\":\"CIF\"}");
    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setCompanyName("Long Address Exporters");
    when(lookup.customer(customerId)).thenReturn(customer);
    Tenant tenant = new Tenant();
    tenant.setCompanyName("FreightFlow Logistics");
    tenant.setCountry("India");
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    AppUser author = new AppUser();
    author.setTenantId(tenantId);
    author.setFullName("Asha Patel");
    when(users.findById(authorId)).thenReturn(Optional.of(author));
    CustomerAddress address = new CustomerAddress();
    address.setAddressType("BUSINESS");
    address.setLine1("Suite 1802, North Sathorn Road, Silom, Bang Rak, Bangkok");
    address.setCity("Bangkok");
    address.setCountry("Thailand");
    when(addresses.findByTenantIdAndCustomerId(tenantId, customerId)).thenReturn(List.of(address));
    QuotationLine usd = new QuotationLine();
    usd.setDescription("Origin charges");
    usd.setQuantity(BigDecimal.ONE);
    usd.setUnitPrice(new BigDecimal("40.00"));
    usd.setAmount(new BigDecimal("40.00"));
    usd.setOriginalCurrency("USD");
    usd.setOriginalUnitPrice(new BigDecimal("40.00"));
    QuotationLine eur = new QuotationLine();
    eur.setDescription("Ocean freight");
    eur.setQuantity(BigDecimal.ONE);
    eur.setUnitPrice(new BigDecimal("108.00"));
    eur.setAmount(new BigDecimal("108.00"));
    eur.setOriginalCurrency("EUR");
    eur.setOriginalUnitPrice(new BigDecimal("100.00"));
    eur.setFxRate(new BigDecimal("1.08"));
    eur.setFxReferenceRate(new BigDecimal("1.07"));
    eur.setFxSource("ECB_REFERENCE");
    eur.setFxObservationDate(LocalDate.of(2026, 10, 10));
    when(lines.findByTenantIdAndQuotationIdOrderBySortOrder(tenantId, quotation.getId()))
        .thenReturn(List.of(usd, eur));

    service.freeze(quotation);
    customer.setCompanyName("Changed after finalization");
    quotation.setStatus("SENT");
    var document = service.document(quotation);
    assertEquals("SENT", document.status());
    assertEquals("Long Address Exporters", document.customerName());
    assertTrue(document.customerAddress().contains("North Sathorn Road"));
    assertEquals(2, document.charges().size());
    assertEquals(
        0, new BigDecimal("108.00").compareTo(document.charges().get(1).convertedAmount()));
    assertEquals(0, new BigDecimal("1.08").compareTo(document.charges().get(1).fxRate()));
    assertEquals(0, new BigDecimal("148.00").compareTo(document.total()));

    var controller =
        new QuoteCommunicationController(
            lookup,
            lines,
            mock(QuoteRepo.class),
            contacts,
            tenants,
            mock(com.freightflow.quotation.repository.EmailLogRepo.class),
            mock(com.freightflow.mail.EmailSender.class),
            mock(QuotationLifecycleService.class),
            mock(com.freightflow.mail.QuotationMailDraftService.class),
            service);
    byte[] pdf = controller.render(quotation);
    assertTrue(pdf.length > 500);
    var reader = new PdfReader(pdf);
    String text = new PdfTextExtractor(reader).getTextFromPage(1);
    assertTrue(text.contains(document.customerName()));
    assertTrue(text.contains("North Sathorn Road"));
    assertTrue(text.contains(document.charges().get(1).description()));
    assertTrue(text.contains("148.00"));
    assertTrue(text.contains("ECB_REFERENCE"));
    assertTrue(text.contains(document.terms()));
  }

  @Test
  void zeroChargesDoNotCreateInventedTotalsOrInclusions() {
    var lookup = mock(QuotationLookupService.class);
    var lines = mock(LineRepo.class);
    var contacts = mock(ContactRepo.class);
    var addresses = mock(AddressRepo.class);
    var tenants = mock(TenantRepo.class);
    var users = mock(UserRepo.class);
    var service =
        new QuotationDocumentService(
            lookup,
            lines,
            contacts,
            addresses,
            tenants,
            users,
            new ObjectMapper().registerModule(new JavaTimeModule()));
    Quotation quotation = new Quotation();
    quotation.setTenantId(UUID.randomUUID());
    quotation.setCustomerId(UUID.randomUUID());
    quotation.setCreatedBy(UUID.randomUUID());
    quotation.setCurrency("USD");
    quotation.setTotalAmount(BigDecimal.ZERO);
    quotation.setShipmentSnapshot("{\"mode\":\"AIR\"}");
    Customer customer = new Customer();
    customer.setCompanyName("Empty Quote Customer");
    when(lookup.customer(quotation.getCustomerId())).thenReturn(customer);
    Tenant tenant = new Tenant();
    tenant.setCompanyName("FreightFlow");
    when(tenants.findById(quotation.getTenantId())).thenReturn(Optional.of(tenant));
    when(lines.findByTenantIdAndQuotationIdOrderBySortOrder(
            quotation.getTenantId(), quotation.getId()))
        .thenReturn(List.of());
    var document = service.document(quotation);
    assertTrue(document.charges().isEmpty());
    assertTrue(document.included().isEmpty());
    assertTrue(document.excluded().isEmpty());
    assertEquals(BigDecimal.ZERO, document.total());
  }
}
