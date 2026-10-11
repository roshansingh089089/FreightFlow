package com.freightflow.customer.repository;

import com.freightflow.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepo extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

  long countByTenantId(UUID tenantId);

  @Query(
      "select c from Customer c where c.tenantId=:t and (:q='' or lower(c.companyName) like lower(concat('%',:q,'%')) or lower(c.customerCode) like lower(concat('%',:q,'%')) or exists(select x.id from CustomerContact x where x.customerId=c.id and x.tenantId=:t and x.primary=true and (lower(x.email) like lower(concat('%',:q,'%')) or x.phone like concat('%',:q,'%')))) and (:type='' or c.customerType=:type) and (:country='' or lower(c.country)=lower(:country)) and (:status='' or c.status=:status) and (:owner is null or c.salesOwnerUserId=:owner)")
  Page<Customer> search(
      @Param("t") UUID t,
      @Param("q") String q,
      @Param("type") String type,
      @Param("country") String country,
      @Param("status") String status,
      @Param("owner") UUID owner,
      Pageable p);

  @Query(
      "select c from Customer c where c.tenantId=:t and (lower(c.companyName) like lower(concat('%',:q,'%')) or lower(c.customerCode) like lower(concat('%',:q,'%'))) order by c.companyName")
  List<Customer> searchTop(UUID t, String q, Pageable pageable);
}
