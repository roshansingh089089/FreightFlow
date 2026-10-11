package com.freightflow.mail.repository;

import com.freightflow.mail.entity.OutboundMessage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundMessageRepo
    extends JpaRepository<OutboundMessage, UUID>, JpaSpecificationExecutor<OutboundMessage> {
  Optional<OutboundMessage> findByIdAndTenantIdAndCreatedBy(UUID id, UUID tenantId, UUID userId);

  Optional<OutboundMessage> findByIdAndTenantIdAndConnectionIdAndStatus(
      UUID id, UUID tenantId, UUID connectionId, String status);

  Optional<OutboundMessage> findByTenantIdAndCreatedByAndIdempotencyKey(
      UUID tenantId, UUID userId, String key);

  Optional<OutboundMessage> findFirstByTenantIdAndConnectionIdAndMessageId(
      UUID tenantId, UUID connectionId, String messageId);

  java.util.List<OutboundMessage> findByTenantIdAndConnectionIdAndThreadIdOrderByCreatedAtAsc(
      UUID tenantId, UUID connectionId, UUID threadId);

  boolean existsByTenantIdAndQuotationIdAndQuotationSendKindAndStatusIn(
      UUID tenantId, UUID quotationId, String kind, java.util.List<String> statuses);

  Page<OutboundMessage> findByTenantIdAndCreatedByAndStatus(
      UUID tenantId, UUID userId, String status, Pageable page);

  Page<OutboundMessage> findByTenantIdAndCreatedBy(UUID tenantId, UUID userId, Pageable page);

  @Query(
      "select m from OutboundMessage m where m.tenantId=:tenant and m.customerId=:customer and (m.createdBy=:viewer or m.status='SENT')")
  Page<OutboundMessage> customerHistory(
      @Param("tenant") UUID tenant,
      @Param("customer") UUID customer,
      @Param("viewer") UUID viewer,
      Pageable page);
}
