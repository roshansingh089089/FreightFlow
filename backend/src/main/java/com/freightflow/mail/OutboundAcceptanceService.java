package com.freightflow.mail;

import com.freightflow.mail.entity.OutboundMessage;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the local consequences of an SMTP acceptance; SMTP itself runs outside this transaction.
 */
@Service
class OutboundAcceptanceService {
  private final JdbcTemplate jdbc;

  OutboundAcceptanceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  void complete(UUID attemptId, OutboundMessage message) {
    int attempt =
        jdbc.update(
            "update mail_send_attempts set status='ACCEPTED',completed_at=coalesce(completed_at,now()),error_code=null "
                + "where id=? and tenant_id=? and message_id=? and status='ACCEPTED'",
            attemptId,
            message.getTenantId(),
            message.getId());
    if (attempt != 1) throw new IllegalStateException("Accepted mail attempt was not found");
    int mail =
        jdbc.update(
            "update outbound_messages set status='SENT',sent_at=coalesce(sent_at,now()),updated_at=now(),error_code=null "
                + "where id=? and tenant_id=? and status in ('SENDING','NEEDS_REVIEW','SENT')",
            message.getId(),
            message.getTenantId());
    if (mail != 1) throw new IllegalStateException("Outbound message could not be reconciled");
    if (message.getQuotationId() != null && "PRIMARY".equals(message.getQuotationSendKind())) {
      int quote =
          jdbc.update(
              "update quotations set status='SENT',sent_at=coalesce(sent_at,now()),updated_at=now() "
                  + "where id=? and tenant_id=? and status in ('READY_TO_SEND','SENT')",
              message.getQuotationId(),
              message.getTenantId());
      if (quote != 1) throw new IllegalStateException("Quotation could not be reconciled");
    }
  }
}
