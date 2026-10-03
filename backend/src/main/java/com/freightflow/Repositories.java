package com.freightflow;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
interface TenantRepo extends JpaRepository<Tenant,UUID>{}
interface UserRepo extends JpaRepository<AppUser,UUID>{Optional<AppUser> findByEmailIgnoreCase(String email);}
interface CustomerRepo extends JpaRepository<Customer,UUID>{ Optional<Customer> findByIdAndTenantId(UUID id,UUID tenantId); long countByTenantId(UUID tenantId); @Query("select c from Customer c where c.tenantId=:t and (:q='' or lower(c.companyName) like lower(concat('%',:q,'%')) or lower(c.customerCode) like lower(concat('%',:q,'%')) or exists(select x.id from CustomerContact x where x.customerId=c.id and x.tenantId=:t and x.primary=true and (lower(x.email) like lower(concat('%',:q,'%')) or x.phone like concat('%',:q,'%')))) and (:type='' or c.customerType=:type) and (:country='' or lower(c.country)=lower(:country)) and (:status='' or c.status=:status)") Page<Customer> search(@Param("t") UUID t,@Param("q") String q,@Param("type") String type,@Param("country") String country,@Param("status") String status,Pageable p); @Query("select c from Customer c where c.tenantId=:t and (lower(c.companyName) like lower(concat('%',:q,'%')) or lower(c.customerCode) like lower(concat('%',:q,'%'))) order by c.companyName") List<Customer> searchTop(UUID t,String q,Pageable pageable); }
interface ContactRepo extends JpaRepository<CustomerContact,UUID>{List<CustomerContact> findByTenantIdAndCustomerIdOrderByPrimaryDesc(UUID t,UUID c); Optional<CustomerContact> findFirstByTenantIdAndCustomerIdAndPrimaryTrue(UUID t,UUID c);}
interface AddressRepo extends JpaRepository<CustomerAddress,UUID>{List<CustomerAddress> findByTenantIdAndCustomerId(UUID t,UUID c);}
interface EnquiryRepo extends JpaRepository<Enquiry,UUID>, JpaSpecificationExecutor<Enquiry> {
 Optional<Enquiry> findByIdAndTenantId(UUID id,UUID t);
 List<Enquiry> findTop5ByTenantIdOrderByCreatedAtDesc(UUID t);
 List<Enquiry> findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID t,UUID c);
 long countByTenantIdAndStatus(UUID t,String s);
 List<Enquiry> findTop5ByTenantIdAndEnquiryCodeContainingIgnoreCase(UUID t,String q);

 default Page<Enquiry> search(UUID tenant,String term,UUID customer,String mode,String status,java.time.Instant from,Pageable page){
  Specification<Enquiry> filters=(root,query,cb)->cb.equal(root.get("tenantId"),tenant);
  if(term!=null&&!term.isBlank()){
   String pattern="%"+term.toLowerCase()+"%";
   filters=filters.and((root,query,cb)->cb.or(
    cb.like(cb.lower(root.get("enquiryCode")),pattern),
    cb.like(cb.lower(root.get("origin")),pattern),
    cb.like(cb.lower(root.get("destination")),pattern)));
  }
  if(customer!=null)filters=filters.and((root,query,cb)->cb.equal(root.get("customerId"),customer));
  if(mode!=null&&!mode.isBlank())filters=filters.and((root,query,cb)->cb.equal(root.get("mode"),mode));
  if(status!=null&&!status.isBlank())filters=filters.and((root,query,cb)->cb.equal(root.get("status"),status));
  if(from!=null)filters=filters.and((root,query,cb)->cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"),from));
  return findAll(filters,page);
 }
}
interface QuoteRepo extends JpaRepository<Quotation,UUID>, JpaSpecificationExecutor<Quotation> {
 Optional<Quotation> findByIdAndTenantId(UUID id,UUID t);
 List<Quotation> findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID t,UUID c);
 long countByTenantIdAndStatus(UUID t,String s);
 List<Quotation> findTop5ByTenantIdAndQuotationNumberContainingIgnoreCase(UUID t,String q);
 List<Quotation> findTop5ByTenantIdAndStatusAndValidUntilLessThanEqual(UUID t,String s,java.time.LocalDate d);

 default Page<Quotation> search(UUID tenant,String term,UUID customer,String status,java.time.Instant from,Pageable page){
  Specification<Quotation> filters=(root,query,cb)->cb.equal(root.get("tenantId"),tenant);
  if(term!=null&&!term.isBlank()){
   String pattern="%"+term.toLowerCase()+"%";
   filters=filters.and((root,query,cb)->cb.like(cb.lower(root.get("quotationNumber")),pattern));
  }
  if(customer!=null)filters=filters.and((root,query,cb)->cb.equal(root.get("customerId"),customer));
  if(status!=null&&!status.isBlank())filters=filters.and((root,query,cb)->cb.equal(root.get("status"),status));
  if(from!=null)filters=filters.and((root,query,cb)->cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"),from));
  return findAll(filters,page);
 }
}
interface LineRepo extends JpaRepository<QuotationLine,UUID>{List<QuotationLine> findByTenantIdAndQuotationIdOrderBySortOrder(UUID t,UUID q); void deleteByTenantIdAndQuotationId(UUID t,UUID q);}
interface EmailLogRepo extends JpaRepository<QuotationEmailLog,UUID>{}
