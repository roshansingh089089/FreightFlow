package com.freightflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.CustomerProfileService;
import com.freightflow.customer.repository.AddressRepo;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.enquiry.dto.request.EnquiryInput;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.enquiry.repository.EnquiryRepo;
import com.freightflow.fx.CurrencyConversionService;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.quotation.QuotationShipmentMapper;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.reference.LocationSearchService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BusinessServiceTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void customerLookupUsesAuthenticatedTenant() {
    CustomerRepo customers = mock(CustomerRepo.class);
    BusinessService b =
        new BusinessService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            customers,
            mock(ContactRepo.class),
            mock(AddressRepo.class),
            mock(EnquiryRepo.class),
            mock(QuoteRepo.class),
            mock(LineRepo.class),
            mock(CustomerProfileService.class),
            mock(com.freightflow.mail.repository.EmailMessageRepo.class),
            mock(com.freightflow.mail.repository.MailConnectionRepo.class),
            mock(LocationSearchService.class),
            mock(QuotationShipmentMapper.class),
            mock(CurrencyConversionService.class));
    AppUser u = new AppUser();
    u.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null));
    UUID id = UUID.randomUUID();
    when(customers.findByIdAndTenantId(id, u.getTenantId())).thenReturn(Optional.empty());
    ApiException e = assertThrows(ApiException.class, () -> b.customer(id));
    assertEquals(404, e.status);
    verify(customers).findByIdAndTenantId(id, u.getTenantId());
    verify(customers, never()).findById(id);
  }

  @Test
  void enquirySourceRequiresMessageInUsersActiveMailbox() throws Exception {
    CustomerRepo customers = mock(CustomerRepo.class);
    EmailMessageRepo sourceMessages = mock(EmailMessageRepo.class);
    MailConnectionRepo mailConnections = mock(MailConnectionRepo.class);
    BusinessService b =
        new BusinessService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            customers,
            mock(ContactRepo.class),
            mock(AddressRepo.class),
            mock(EnquiryRepo.class),
            mock(QuoteRepo.class),
            mock(LineRepo.class),
            mock(CustomerProfileService.class),
            sourceMessages,
            mailConnections,
            mock(LocationSearchService.class),
            mock(QuotationShipmentMapper.class),
            mock(CurrencyConversionService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID customerId = UUID.randomUUID(), sourceId = UUID.randomUUID();
    when(customers.findByIdAndTenantId(customerId, user.getTenantId()))
        .thenReturn(Optional.of(new com.freightflow.customer.entity.Customer()));
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    when(mailConnections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    EnquiryInput input =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                "{\"customerId\":\""
                    + customerId
                    + "\",\"sourceMessageId\":\""
                    + sourceId
                    + "\",\"mode\":\"ROAD\",\"origin\":\"A\",\"destination\":\"B\",\"commodity\":\"Cargo\"}",
                EnquiryInput.class);
    ApiException error = assertThrows(ApiException.class, () -> b.apply(new Enquiry(), input));
    assertEquals("MAIL_NOT_FOUND", error.code);
    verify(sourceMessages)
        .findByIdAndTenantIdAndConnectionId(sourceId, user.getTenantId(), connection.getId());
    var source = new com.freightflow.mail.entity.EmailMessage();
    source.setId(sourceId);
    UUID threadId = UUID.randomUUID();
    source.setThreadId(threadId);
    when(sourceMessages.findByIdAndTenantIdAndConnectionId(
            sourceId, user.getTenantId(), connection.getId()))
        .thenReturn(Optional.of(source));
    Enquiry enquiry = new Enquiry();
    b.apply(enquiry, input);
    assertEquals(sourceId, enquiry.getSourceMessageId());
    assertEquals(threadId, enquiry.getSourceThreadId());
    assertEquals(connection.getId(), enquiry.getSourceConnectionId());
  }

  @Test
  void historicalTextOnlyRouteSurvivesUnrelatedEnquiryEdit() throws Exception {
    CustomerRepo customers = mock(CustomerRepo.class);
    LocationSearchService locations = mock(LocationSearchService.class);
    BusinessService business =
        new BusinessService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            customers,
            mock(ContactRepo.class),
            mock(AddressRepo.class),
            mock(EnquiryRepo.class),
            mock(QuoteRepo.class),
            mock(LineRepo.class),
            mock(CustomerProfileService.class),
            mock(EmailMessageRepo.class),
            mock(MailConnectionRepo.class),
            locations,
            mock(QuotationShipmentMapper.class),
            mock(CurrencyConversionService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID customerId = UUID.randomUUID();
    when(customers.findByIdAndTenantId(customerId, user.getTenantId()))
        .thenReturn(Optional.of(new com.freightflow.customer.entity.Customer()));
    Enquiry existing = new Enquiry();
    existing.setMode("OCEAN");
    existing.setOrigin("Laem Chabang");
    existing.setDestination("Nhava Sheva");
    existing.setPortOfLoading("Laem Chabang");
    existing.setPortOfDischarge("Nhava Sheva");
    EnquiryInput input =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                "{\"customerId\":\""
                    + customerId
                    + "\",\"mode\":\"OCEAN\",\"origin\":\"Laem Chabang\",\"destination\":\"Nhava Sheva\",\"portOfLoading\":\"Laem Chabang\",\"portOfDischarge\":\"Nhava Sheva\",\"commodity\":\"Cargo\",\"notes\":\"Updated\"}",
                EnquiryInput.class);
    business.apply(existing, input, false);
    assertEquals("Updated", existing.getNotes());
    assertEquals(null, existing.getPolCode());
    verifyNoInteractions(locations);
  }

  @Test
  void completedEnquiryCanBeSavedAsDraft() throws Exception {
    CustomerRepo customers = mock(CustomerRepo.class);
    BusinessService business =
        new BusinessService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            customers,
            mock(ContactRepo.class),
            mock(AddressRepo.class),
            mock(EnquiryRepo.class),
            mock(QuoteRepo.class),
            mock(LineRepo.class),
            mock(CustomerProfileService.class),
            mock(EmailMessageRepo.class),
            mock(MailConnectionRepo.class),
            mock(LocationSearchService.class),
            mock(QuotationShipmentMapper.class),
            mock(CurrencyConversionService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID customerId = UUID.randomUUID();
    when(customers.findByIdAndTenantId(customerId, user.getTenantId()))
        .thenReturn(Optional.of(new com.freightflow.customer.entity.Customer()));
    EnquiryInput input =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                "{\"customerId\":\""
                    + customerId
                    + "\",\"mode\":\"ROAD\",\"origin\":\"Bangkok\",\"destination\":\"Chennai\",\"commodity\":\"Machinery\",\"status\":\"DRAFT\"}",
                EnquiryInput.class);
    Enquiry enquiry = new Enquiry();
    business.apply(enquiry, input, true);
    assertEquals("DRAFT", enquiry.getStatus());
  }

  @Test
  void salesTrackerScopeAndCurrencyAreValidatedAndStored() throws Exception {
    CustomerRepo customers = mock(CustomerRepo.class);
    BusinessService business =
        new BusinessService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            customers,
            mock(ContactRepo.class),
            mock(AddressRepo.class),
            mock(EnquiryRepo.class),
            mock(QuoteRepo.class),
            mock(LineRepo.class),
            mock(CustomerProfileService.class),
            mock(EmailMessageRepo.class),
            mock(MailConnectionRepo.class),
            mock(LocationSearchService.class),
            mock(QuotationShipmentMapper.class),
            mock(CurrencyConversionService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID customerId = UUID.randomUUID();
    when(customers.findByIdAndTenantId(customerId, user.getTenantId()))
        .thenReturn(Optional.of(new com.freightflow.customer.entity.Customer()));
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String base =
        "{\"customerId\":\""
            + customerId
            + "\",\"mode\":\"ROAD\",\"origin\":\"Bangkok\",\"destination\":\"Chennai\",\"commodity\":\"Machinery\",\"incoterm\":\"EXW\",";
    EnquiryInput valid =
        mapper.readValue(
            base
                + "\"serviceScope\":[\"Pickup & trucking\",\"Road freight\",\"Warehousing\"],\"quoteCurrency\":\"THB\"}",
            EnquiryInput.class);
    Enquiry enquiry = new Enquiry();
    business.apply(enquiry, valid, true);
    assertEquals("THB", enquiry.getQuoteCurrency());
    assertEquals(3, enquiry.getServiceScope().size());
    assertTrue(enquiry.isPickupRequired());

    EnquiryInput missingFreight =
        mapper.readValue(
            base + "\"serviceScope\":[\"Pickup & trucking\"],\"quoteCurrency\":\"THB\"}",
            EnquiryInput.class);
    assertThrows(ApiException.class, () -> business.apply(new Enquiry(), missingFreight, true));
  }
}
