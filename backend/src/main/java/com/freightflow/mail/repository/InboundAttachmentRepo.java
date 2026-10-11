package com.freightflow.mail.repository;

import com.freightflow.mail.entity.InboundAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundAttachmentRepo extends JpaRepository<InboundAttachment, UUID> {
  List<InboundAttachment> findByTenantIdAndConnectionIdAndMessageId(
      UUID tenantId, UUID connectionId, UUID messageId);

  Optional<InboundAttachment> findByIdAndTenantIdAndConnectionIdAndMessageId(
      UUID id, UUID tenantId, UUID connectionId, UUID messageId);
}
