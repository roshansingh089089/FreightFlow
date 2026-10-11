package com.freightflow.mail.repository;

import com.freightflow.mail.entity.MailThread;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailThreadRepo extends JpaRepository<MailThread, UUID> {
  Optional<MailThread> findByIdAndTenantIdAndConnectionId(
      UUID id, UUID tenantId, UUID connectionId);

  Page<MailThread> findByTenantIdAndConnectionIdOrderByLastActivityAtDesc(
      UUID tenantId, UUID connectionId, Pageable pageable);

  Page<MailThread> findByTenantIdAndConnectionIdAndCustomerIdOrderByLastActivityAtDesc(
      UUID tenantId, UUID connectionId, UUID customerId, Pageable pageable);
}
