package com.freightflow.enquiry.repository;

import com.freightflow.enquiry.entity.Enquiry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EnquiryRepo
    extends JpaRepository<Enquiry, UUID>, JpaSpecificationExecutor<Enquiry> {
  Optional<Enquiry> findByIdAndTenantId(UUID id, UUID t);

  Optional<Enquiry> findByTenantIdAndSourceMessageId(UUID tenantId, UUID sourceMessageId);

  List<Enquiry> findTop5ByTenantIdOrderByCreatedAtDesc(UUID t);

  List<Enquiry> findTop5ByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID t, UUID c);

  long countByTenantIdAndStatus(UUID t, String s);

  List<Enquiry> findTop5ByTenantIdAndEnquiryCodeContainingIgnoreCase(UUID t, String q);

  default Page<Enquiry> search(
      UUID tenant,
      String term,
      UUID customer,
      String mode,
      String status,
      java.time.Instant from,
      Pageable page) {
    Specification<Enquiry> filters = (root, query, cb) -> cb.equal(root.get("tenantId"), tenant);
    if (term != null && !term.isBlank()) {
      String pattern = "%" + term.toLowerCase() + "%";
      filters =
          filters.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("enquiryCode")), pattern),
                      cb.like(cb.lower(root.get("origin")), pattern),
                      cb.like(cb.lower(root.get("destination")), pattern)));
    }
    if (customer != null)
      filters = filters.and((root, query, cb) -> cb.equal(root.get("customerId"), customer));
    if (mode != null && !mode.isBlank())
      filters = filters.and((root, query, cb) -> cb.equal(root.get("mode"), mode));
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
