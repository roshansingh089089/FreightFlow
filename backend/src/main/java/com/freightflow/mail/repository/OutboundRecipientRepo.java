package com.freightflow.mail.repository;

import com.freightflow.mail.entity.OutboundRecipient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundRecipientRepo extends JpaRepository<OutboundRecipient, UUID> {
  List<OutboundRecipient> findByTenantIdAndMessageId(UUID tenantId, UUID messageId);

  void deleteByTenantIdAndMessageId(UUID tenantId, UUID messageId);
}
