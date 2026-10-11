package com.freightflow.customer.repository;

import com.freightflow.customer.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepo extends JpaRepository<CustomerAddress, UUID> {
  List<CustomerAddress> findByTenantIdAndCustomerId(UUID t, UUID c);

  Optional<CustomerAddress> findByIdAndTenantIdAndCustomerId(UUID id, UUID t, UUID c);
}
