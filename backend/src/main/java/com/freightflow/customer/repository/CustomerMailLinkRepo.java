package com.freightflow.customer.repository;

import com.freightflow.customer.entity.CustomerMailLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerMailLinkRepo extends JpaRepository<CustomerMailLink, UUID> {
  List<CustomerMailLink> findByTenantIdAndCustomerIdOrderByLinkedAtDesc(UUID t, UUID c);

  List<CustomerMailLink> findByTenantIdAndCustomerIdOrderByLinkedAtDesc(
      UUID t, UUID c, Pageable page);

  Optional<CustomerMailLink> findByTenantIdAndMessageId(UUID t, UUID m);
}
