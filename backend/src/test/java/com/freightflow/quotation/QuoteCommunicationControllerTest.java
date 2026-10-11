package com.freightflow.quotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.mail.EmailSender;
import com.freightflow.mail.QuotationMailDraftService;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.repository.EmailLogRepo;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.tenant.entity.Tenant;
import com.freightflow.tenant.repository.TenantRepo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class QuoteCommunicationControllerTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void draftCannotBeSent() {
    QuotationLookupService b = mock(QuotationLookupService.class);
    EmailSender mail = mock(EmailSender.class);
    QuoteCommunicationController c =
        new QuoteCommunicationController(
            b,
            mock(LineRepo.class),
            mock(QuoteRepo.class),
            mock(ContactRepo.class),
            mock(TenantRepo.class),
            mock(EmailLogRepo.class),
            mail,
            mock(QuotationLifecycleService.class),
            mock(QuotationMailDraftService.class),
            mock(QuotationDocumentService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID id = UUID.randomUUID();
    when(b.quote(id)).thenReturn(new Quotation());
    ApiException error =
        assertThrows(
            ApiException.class,
            () ->
                c.send(
                    id,
                    new QuoteCommunicationController.SendRequest(
                        "buyer@example.test", "", "Subject", "Body")));
    assertEquals("QUOTE_NOT_FINALIZED", error.code);
    verify(mail, never())
        .send(anyString(), anyString(), anyString(), anyString(), anyString(), any(byte[].class));
  }

  @Test
  void missingEmailConfigurationDoesNotAttemptDelivery() {
    QuotationLookupService b = mock(QuotationLookupService.class);
    QuoteRepo quotes = mock(QuoteRepo.class);
    EmailLogRepo logs = mock(EmailLogRepo.class);
    EmailSender mail = mock(EmailSender.class);
    QuoteCommunicationController c =
        new QuoteCommunicationController(
            b,
            mock(LineRepo.class),
            quotes,
            mock(ContactRepo.class),
            mock(TenantRepo.class),
            logs,
            mail,
            mock(QuotationLifecycleService.class),
            mock(QuotationMailDraftService.class),
            mock(QuotationDocumentService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID id = UUID.randomUUID();
    Quotation quote = new Quotation();
    quote.setTenantId(user.getTenantId());
    quote.setStatus("READY_TO_SEND");
    when(b.quote(id)).thenReturn(quote);
    ApiException error =
        assertThrows(
            ApiException.class,
            () ->
                c.send(
                    id,
                    new QuoteCommunicationController.SendRequest(
                        "buyer@example.test", "", "Subject", "Body")));
    assertEquals("EMAIL_NOT_CONFIGURED", error.code);
    verify(mail, never())
        .send(anyString(), anyString(), anyString(), anyString(), anyString(), any(byte[].class));
    verify(logs, never()).save(any());
  }

  @Test
  void previewDoesNotSendAndSendRequiresExplicitCall() {
    QuotationLookupService b = mock(QuotationLookupService.class);
    ContactRepo contacts = mock(ContactRepo.class);
    TenantRepo tenants = mock(TenantRepo.class);
    EmailLogRepo logs = mock(EmailLogRepo.class);
    EmailSender mail = mock(EmailSender.class);
    QuoteRepo quotes = mock(QuoteRepo.class);
    QuotationLifecycleService lifecycle = mock(QuotationLifecycleService.class);
    QuoteCommunicationController c =
        spy(
            new QuoteCommunicationController(
                b,
                mock(LineRepo.class),
                quotes,
                contacts,
                tenants,
                logs,
                mail,
                lifecycle,
                mock(QuotationMailDraftService.class),
                mock(QuotationDocumentService.class)));
    UUID id = UUID.randomUUID(),
        tenant = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        enquiryId = UUID.randomUUID();
    AppUser user = new AppUser();
    user.setFullName("Agent One");
    user.setTenantId(tenant);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    Quotation quote = new Quotation();
    quote.setTenantId(tenant);
    quote.setStatus("READY_TO_SEND");
    quote.setCustomerId(customerId);
    quote.setEnquiryId(enquiryId);
    quote.setQuotationNumber("QT-2026-000001");
    quote.setCurrency("USD");
    quote.setTotalAmount(new BigDecimal("100.00"));
    quote.setValidUntil(LocalDate.now().plusDays(7));
    Enquiry enquiry = new Enquiry();
    enquiry.setOrigin("Mumbai");
    enquiry.setDestination("Hamburg");
    enquiry.setMode("OCEAN");
    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setCompanyName("Acme");
    CustomerContact contact = new CustomerContact();
    contact.setName("Buyer");
    contact.setEmail("buyer@example.test");
    Tenant company = new Tenant();
    company.setCompanyName("Forwarder");
    when(b.quote(id)).thenReturn(quote);
    when(b.enquiry(enquiryId)).thenReturn(enquiry);
    when(b.customer(customerId)).thenReturn(customer);
    when(contacts.findFirstByTenantIdAndCustomerIdAndPrimaryTrue(tenant, customerId))
        .thenReturn(Optional.of(contact));
    when(tenants.findById(tenant)).thenReturn(Optional.of(company));
    when(mail.configured()).thenReturn(true);
    when(lifecycle.claimSend(eq(quote), anyString())).thenReturn("CLAIMED");
    doReturn(new byte[] {1, 2, 3}).when(c).render(quote);
    var preview = c.preview(id);
    assertEquals("buyer@example.test", preview.to());
    verify(mail, never())
        .send(anyString(), anyString(), anyString(), anyString(), anyString(), any(byte[].class));
    assertEquals("READY_TO_SEND", quote.getStatus());
    c.send(
        id,
        new QuoteCommunicationController.SendRequest(
            preview.to(), "", preview.subject(), preview.body()));
    verify(mail)
        .send(
            eq("buyer@example.test"),
            eq(""),
            eq(preview.subject()),
            eq(preview.body()),
            eq("QT-2026-000001.pdf"),
            argThat(bytes -> Arrays.equals(bytes, new byte[] {1, 2, 3})));
    verify(quotes).save(quote);
    verify(logs).save(argThat(log -> log.getStatus().equals("SENT")));
    assertEquals("SENT", quote.getStatus());
  }
}
