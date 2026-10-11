package com.freightflow.quotation;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class QuotationLifecycleService {
  private final QuotationLookupService business;
  private final QuoteRepo quotes;
  private final LineRepo lines;
  private final JdbcTemplate jdbc;
  private final QuotationDocumentService documents;

  QuotationLifecycleService(
      QuotationLookupService business,
      QuoteRepo quotes,
      LineRepo lines,
      JdbcTemplate jdbc,
      QuotationDocumentService documents) {
    this.business = business;
    this.quotes = quotes;
    this.lines = lines;
    this.jdbc = jdbc;
    this.documents = documents;
  }

  @Transactional
  Map<String, String> finalizeQuote(UUID id) {
    Quotation q = business.quote(id);
    if (!"DRAFT".equals(q.getStatus()))
      throw new ApiException(409, "QUOTE_STATUS", "Only drafts can be finalized");
    if (q.getValidUntil() == null || q.getValidUntil().isBefore(LocalDate.now()))
      throw new ApiException(400, "QUOTE_VALIDITY", "Choose a current valid-until date");
    if (lines.findByTenantIdAndQuotationIdOrderBySortOrder(q.getTenantId(), id).isEmpty()
        || q.getTotalAmount() == null
        || q.getTotalAmount().signum() <= 0)
      throw new ApiException(400, "QUOTE_CHARGES", "Add at least one priced charge");
    q.setStatus("READY_TO_SEND");
    q.setFxFrozenAt(Instant.now());
    documents.freeze(q);
    quotes.save(q);
    return Map.of("status", "READY_TO_SEND");
  }

  String claimSend(Quotation q, String key) {
    AppUser user = SecurityConfig.current();
    if (key == null || key.isBlank() || key.length() > 100)
      throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", "Provide an idempotency key");
    int claimed =
        jdbc.update(
            "insert into quotation_send_claims(id,tenant_id,quotation_id,requested_by,idempotency_key,status) values (?,?,?,?,?,'SENDING') on conflict do nothing",
            UUID.randomUUID(),
            user.getTenantId(),
            q.getId(),
            user.getId(),
            key);
    if (claimed == 1) return "CLAIMED";
    String status =
        jdbc.query(
            "select status from quotation_send_claims where tenant_id=? and quotation_id=? and requested_by=? and idempotency_key=?",
            rs -> rs.next() ? rs.getString(1) : null,
            user.getTenantId(),
            q.getId(),
            user.getId(),
            key);
    if (status == null)
      throw new ApiException(409, "QUOTE_ALREADY_SUBMITTED", "Quotation was already submitted");
    return status;
  }

  void finishSend(Quotation q, boolean success) {
    jdbc.update(
        "update quotation_send_claims set status=?,updated_at=now() where tenant_id=? and quotation_id=?",
        success ? "SENT" : "NEEDS_REVIEW",
        q.getTenantId(),
        q.getId());
  }
}
