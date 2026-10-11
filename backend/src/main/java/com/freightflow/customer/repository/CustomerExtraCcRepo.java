package com.freightflow.customer.repository;

import com.freightflow.customer.entity.CustomerExtraCc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerExtraCcRepo extends JpaRepository<CustomerExtraCc, UUID> {
  List<CustomerExtraCc> findByTenantIdAndCustomerIdAndActiveTrue(UUID t, UUID c);

  Optional<CustomerExtraCc> findByIdAndTenantIdAndCustomerId(UUID id, UUID t, UUID c);
}
