package com.freightflow.mail.repository;

import com.freightflow.mail.entity.MailConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailConnectionRepo extends JpaRepository<MailConnection, UUID> {
  Optional<MailConnection> findByTenantIdAndUserIdAndProvider(
      UUID tenantId, UUID userId, String provider);

  Optional<MailConnection> findByTenantIdAndUserIdAndProviderAndEmailAddressIgnoreCase(
      UUID tenantId, UUID userId, String provider, String emailAddress);

  Optional<MailConnection> findByTenantIdAndUserIdAndActiveTrue(UUID tenantId, UUID userId);

  Optional<MailConnection> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

  Optional<MailConnection> findFirstByTenantIdAndUserIdOrderByUpdatedAtDesc(
      UUID tenantId, UUID userId);
}
