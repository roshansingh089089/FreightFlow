package com.freightflow.quotation.repository;

import com.freightflow.quotation.entity.QuotationLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepo extends JpaRepository<QuotationLine, UUID> {
  List<QuotationLine> findByTenantIdAndQuotationIdOrderBySortOrder(UUID t, UUID q);

  void deleteByTenantIdAndQuotationId(UUID t, UUID q);
}
