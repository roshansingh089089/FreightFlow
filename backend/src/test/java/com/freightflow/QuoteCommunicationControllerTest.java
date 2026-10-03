package com.freightflow;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QuoteCommunicationControllerTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void missingSmtpConfigurationDoesNotAttemptDelivery(){
  BusinessController b=mock(BusinessController.class);QuoteRepo quotes=mock(QuoteRepo.class);EmailLogRepo logs=mock(EmailLogRepo.class);JavaMailSender mail=mock(JavaMailSender.class);
  QuoteCommunicationController c=new QuoteCommunicationController(b,mock(LineRepo.class),quotes,mock(ContactRepo.class),mock(TenantRepo.class),logs,mail);
  AppUser user=new AppUser();user.setTenantId(UUID.randomUUID());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
  UUID id=UUID.randomUUID();Quotation quote=new Quotation();quote.setTenantId(user.getTenantId());when(b.quote(id)).thenReturn(quote);
  ApiException error=assertThrows(ApiException.class,()->c.send(id,new QuoteCommunicationController.SendRequest("buyer@example.test","","Subject","Body")));
  assertEquals("SMTP_NOT_CONFIGURED",error.code);verify(mail,never()).send(any(MimeMessage.class));verify(logs).save(argThat(log->log.getStatus().equals("FAILED")));
 }

 @Test void previewDoesNotSendAndSendRequiresExplicitCall(){BusinessController b=mock(BusinessController.class);ContactRepo contacts=mock(ContactRepo.class);TenantRepo tenants=mock(TenantRepo.class);EmailLogRepo logs=mock(EmailLogRepo.class);JavaMailSender mail=mock(JavaMailSender.class);QuoteRepo quotes=mock(QuoteRepo.class);QuoteCommunicationController c=spy(new QuoteCommunicationController(b,mock(LineRepo.class),quotes,contacts,tenants,logs,mail));ReflectionTestUtils.setField(c,"from","sender@example.test");ReflectionTestUtils.setField(c,"configuredHost","localhost");ReflectionTestUtils.setField(c,"configuredFrom","sender@example.test");UUID id=UUID.randomUUID(),tenant=UUID.randomUUID(),customerId=UUID.randomUUID(),enquiryId=UUID.randomUUID();AppUser user=new AppUser();user.setFullName("Agent One");user.setTenantId(tenant);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));Quotation quote=new Quotation();quote.setTenantId(tenant);quote.setCustomerId(customerId);quote.setEnquiryId(enquiryId);quote.setQuotationNumber("QT-2026-000001");quote.setCurrency("USD");quote.setTotalAmount(new BigDecimal("100.00"));quote.setValidUntil(LocalDate.now().plusDays(7));Enquiry enquiry=new Enquiry();enquiry.setOrigin("Mumbai");enquiry.setDestination("Hamburg");enquiry.setMode("OCEAN");Customer customer=new Customer();customer.setId(customerId);customer.setCompanyName("Acme");CustomerContact contact=new CustomerContact();contact.setName("Buyer");contact.setEmail("buyer@example.test");Tenant company=new Tenant();company.setCompanyName("Forwarder");when(b.quote(id)).thenReturn(quote);when(b.enquiry(enquiryId)).thenReturn(enquiry);when(b.customer(customerId)).thenReturn(customer);when(contacts.findFirstByTenantIdAndCustomerIdAndPrimaryTrue(tenant,customerId)).thenReturn(Optional.of(contact));when(tenants.findById(tenant)).thenReturn(Optional.of(company));when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));doReturn(new byte[]{1,2,3}).when(c).render(quote);
 var preview=c.preview(id);assertEquals("buyer@example.test",preview.to());verify(mail,never()).send(any(MimeMessage.class));assertEquals("DRAFT",quote.getStatus());
 c.send(id,new QuoteCommunicationController.SendRequest(preview.to(),"",preview.subject(),preview.body()));verify(mail).send(any(MimeMessage.class));verify(quotes).save(quote);verify(logs).save(argThat(log->log.getStatus().equals("SENT")));assertEquals("SENT",quote.getStatus());}
}
