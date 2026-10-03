package com.freightflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiAssistanceServiceTest {
 final AiModelClient model=mock(AiModelClient.class);final EnquiryRepo enquiries=mock(EnquiryRepo.class);final QuoteRepo quotes=mock(QuoteRepo.class);final LineRepo lines=mock(LineRepo.class);final CustomerRepo customers=mock(CustomerRepo.class);final ContactRepo contacts=mock(ContactRepo.class);final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 AiAssistanceService service(){return new AiAssistanceService(model,new ObjectMapper().registerModule(new JavaTimeModule()),enquiries,quotes,lines,customers,contacts,jdbc);}
 AppUser user(){AppUser u=new AppUser();u.setId(UUID.randomUUID());u.setTenantId(UUID.randomUUID());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null));return u;}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void otherTenantEnquiryCannotBeUsedForDraft(){AppUser u=user();UUID id=UUID.randomUUID();when(enquiries.findByIdAndTenantId(id,u.getTenantId())).thenReturn(Optional.empty());ApiException error=assertThrows(ApiException.class,()->service().draftClarification(id));assertEquals(404,error.status);verify(enquiries).findByIdAndTenantId(id,u.getTenantId());verifyNoInteractions(model);}
 @Test void otherTenantQuoteCannotBeChecked(){AppUser u=user();UUID id=UUID.randomUUID();when(quotes.findByIdAndTenantId(id,u.getTenantId())).thenReturn(Optional.empty());ApiException error=assertThrows(ApiException.class,()->service().quoteCheck(id));assertEquals(404,error.status);verify(quotes).findByIdAndTenantId(id,u.getTenantId());verifyNoInteractions(model);}
 @Test void quoteCheckReturnsSuggestionsWithoutChangingAmounts(){AppUser u=user();UUID id=UUID.randomUUID();UUID enquiryId=UUID.randomUUID();UUID customerId=UUID.randomUUID();Quotation q=new Quotation();q.setEnquiryId(enquiryId);q.setCustomerId(customerId);q.setCurrency("USD");q.setValidUntil(LocalDate.of(2026,10,10));q.setQuotationNumber("QT-1");Enquiry e=new Enquiry();e.setCustomerId(customerId);e.setMode("OCEAN");e.setOrigin("Mumbai");e.setDestination("Hamburg");e.setCommodity("Garments");e.setReadyDate(LocalDate.of(2026,10,15));Customer c=new Customer();c.setCompanyName("Acme");when(quotes.findByIdAndTenantId(id,u.getTenantId())).thenReturn(Optional.of(q));when(enquiries.findByIdAndTenantId(enquiryId,u.getTenantId())).thenReturn(Optional.of(e));when(customers.findByIdAndTenantId(customerId,u.getTenantId())).thenReturn(Optional.of(c));when(lines.findByTenantIdAndQuotationIdOrderBySortOrder(u.getTenantId(),id)).thenReturn(List.of());when(model.generate(anyString(),anyString(),anyMap())).thenReturn(new ObjectMapper().createObjectNode().set("suggestions",new ObjectMapper().createArrayNode()));Object result=service().quoteCheck(id);assertTrue(result.toString().contains("Cargo ready date is later"));verify(quotes).findByIdAndTenantId(id,u.getTenantId());}
 @Test void draftAsksOnlyForDeterministicMissingInformation(){AppUser u=user();UUID id=UUID.randomUUID();UUID customerId=UUID.randomUUID();Enquiry e=new Enquiry();e.setCustomerId(customerId);e.setMode("AIR");e.setOrigin("Delhi");e.setDestination("Dubai");e.setCommodity("Electronics");Customer c=new Customer();c.setCompanyName("Acme");when(enquiries.findByIdAndTenantId(id,u.getTenantId())).thenReturn(Optional.of(e));when(customers.findByIdAndTenantId(customerId,u.getTenantId())).thenReturn(Optional.of(c));when(model.generate(anyString(),anyString(),anyMap())).thenReturn(new ObjectMapper().createObjectNode().put("text","Please confirm gross weight."));Object result=service().draftClarification(id);assertTrue(result.toString().contains("Gross weight"));verify(model).generate(contains("asking only"),contains("Gross weight"),anyMap());}
}
