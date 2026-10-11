package com.freightflow.customer.repository;

import com.freightflow.customer.entity.CustomerContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepo extends JpaRepository<CustomerContact, UUID> {
  List<CustomerContact> findByTenantIdAndCustomerIdOrderByPrimaryDesc(UUID t, UUID c);

  Optional<CustomerContact> findFirstByTenantIdAndCustomerIdAndPrimaryTrue(UUID t, UUID c);

  Optional<CustomerContact> findByIdAndTenantIdAndCustomerId(UUID id, UUID t, UUID c);

  List<CustomerContact> findByTenantIdAndCustomerIdAndAutoCcTrueAndActiveTrue(UUID t, UUID c);

  List<CustomerContact> findByTenantIdAndEmailIgnoreCaseAndActiveTrue(UUID tenantId, String email);
}
