package com.freightflow.quotation.repository;

import com.freightflow.quotation.entity.Quotation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepo
    extends JpaRepository<Quotation, UUID>, JpaSpecificationExecutor<Quotation> {
  Optional<Quotation> findByIdAndTenantId(UUID id, UUID t);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quotation q where q.id=:id and q.tenantId=:tenantId")
  Optional<Quotation> lockForMailSubmission(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<Quotation> findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID t, UUID c);

  long countByTenantIdAndStatus(UUID t, String s);

  List<Quotation> findTop5ByTenantIdAndQuotationNumberContainingIgnoreCase(UUID t, String q);

  List<Quotation> findTop5ByTenantIdAndStatusAndValidUntilLessThanEqual(
      UUID t, String s, java.time.LocalDate d);

  default Page<Quotation> search(
      UUID tenant,
      String term,
      UUID customer,
      String status,
      java.time.Instant from,
      Pageable page) {
    Specification<Quotation> filters = (root, query, cb) -> cb.equal(root.get("tenantId"), tenant);
    if (term != null && !term.isBlank()) {
      String pattern = "%" + term.toLowerCase() + "%";
      filters =
          filters.and((root, query, cb) -> cb.like(cb.lower(root.get("quotationNumber")), pattern));
    }
    if (customer != null)
      filters = filters.and((root, query, cb) -> cb.equal(root.get("customerId"), customer));
    if (status != null && !status.isBlank())
      filters = filters.and((root, query, cb) -> cb.equal(root.get("status"), status));
    if (from != null)
      filters =
          filters.and(
              (root, query, cb) ->
                  cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"), from));
    return findAll(filters, page);
  }
}
