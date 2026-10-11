package com.freightflow.common.audit.repository;

import com.freightflow.common.audit.entity.BusinessAuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessAuditRepo extends JpaRepository<BusinessAuditEvent, UUID> {
  List<BusinessAuditEvent> findTop50ByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
      UUID t, String type, UUID id);
}
