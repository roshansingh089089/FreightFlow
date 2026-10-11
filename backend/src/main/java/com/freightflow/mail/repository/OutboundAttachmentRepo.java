package com.freightflow.mail.repository;

import com.freightflow.mail.entity.OutboundAttachment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundAttachmentRepo extends JpaRepository<OutboundAttachment, UUID> {
  Optional<OutboundAttachment> findByIdAndTenantId(UUID id, UUID tenantId);

  List<OutboundAttachment> findByTenantIdAndMessageId(UUID tenantId, UUID messageId);

  List<OutboundAttachment> findByMessageIdIsNullAndCreatedAtBefore(Instant before);
}
