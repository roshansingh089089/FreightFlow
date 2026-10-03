package com.freightflow;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BusinessControllerTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void customerLookupUsesAuthenticatedTenant(){CustomerRepo customers=mock(CustomerRepo.class);BusinessController b=new BusinessController(mock(org.springframework.jdbc.core.JdbcTemplate.class),customers,mock(ContactRepo.class),mock(AddressRepo.class),mock(EnquiryRepo.class),mock(QuoteRepo.class),mock(LineRepo.class));AppUser u=new AppUser();u.setTenantId(UUID.randomUUID());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null));UUID id=UUID.randomUUID();when(customers.findByIdAndTenantId(id,u.getTenantId())).thenReturn(Optional.empty());ApiException e=assertThrows(ApiException.class,()->b.customer(id));assertEquals(404,e.status);verify(customers).findByIdAndTenantId(id,u.getTenantId());verify(customers,never()).findById(id);}
}
