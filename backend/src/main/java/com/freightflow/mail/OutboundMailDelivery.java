package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.entity.OutboundAttachment;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.entity.OutboundRecipient;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.mail.repository.OutboundAttachmentRepo;
import com.freightflow.mail.repository.OutboundMessageRepo;
import com.freightflow.mail.repository.OutboundRecipientRepo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboundSmtpTransport {
  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;
  private final OutboundRecipientRepo recipients;
  private final OutboundAttachmentRepo attachments;
  private final MailAttachmentService attachmentService;
  private final JdbcTemplate jdbc;

  OutboundSmtpTransport(
      MailConnectionRepo connections,
      MailCredentialCipher cipher,
      MailTransportSupport transport,
      OutboundRecipientRepo recipients,
      OutboundAttachmentRepo attachments,
      MailAttachmentService attachmentService,
      JdbcTemplate jdbc) {
    this.connections = connections;
    this.cipher = cipher;
    this.transport = transport;
    this.recipients = recipients;
    this.attachments = attachments;
    this.attachmentService = attachmentService;
    this.jdbc = jdbc;
  }

  void send(OutboundMessage message) throws MessagingException {
    MailConnection c =
        connections
            .findByIdAndTenantIdAndUserId(
                message.getConnectionId(), message.getTenantId(), message.getCreatedBy())
            .orElseThrow(
                () ->
                    new ApiException(
                        409, "MAIL_CONNECTION_INVALID", "Sending mailbox is unavailable"));
    if (!"IMAP_SMTP".equals(c.getProvider()) || !c.isActive() || !"CONNECTED".equals(c.getStatus()))
      throw new ApiException(409, "MAIL_CONNECTION_INVALID", "Sending mailbox is unavailable");
    transport.validate(c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode());
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(c.getSmtpHost());
    sender.setPort(c.getSmtpPort());
    sender.setUsername(c.getSmtpUsername());
    sender.setPassword(cipher.decrypt(c.getSmtpPasswordEncrypted()));
    sender.setJavaMailProperties(
        transport
            .session("smtp", c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode())
            .getProperties());
    MimeMessage mime =
        new MimeMessage(sender.getSession()) {
          @Override
          protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", message.getMessageId());
          }
        };
    MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
    helper.setFrom(c.getSmtpFrom());
    helper.setReplyTo(c.getEmailAddress());
    List<OutboundRecipient> rows =
        recipients.findByTenantIdAndMessageId(message.getTenantId(), message.getId());
    helper.setTo(addresses(rows, "TO"));
    String[] cc = addresses(rows, "CC"), bcc = addresses(rows, "BCC");
    if (cc.length > 0) helper.setCc(cc);
    if (bcc.length > 0) helper.setBcc(bcc);
    helper.setSubject(message.getSubject());
    helper.setText(message.getBodyText(), false);
    mime.setHeader("Message-ID", message.getMessageId());
    if (message.getInReplyTo() != null && !message.getInReplyTo().isBlank())
      mime.setHeader("In-Reply-To", message.getInReplyTo());
    if (message.getReferenceHeaders() != null && !message.getReferenceHeaders().isBlank())
      mime.setHeader("References", message.getReferenceHeaders());
    for (OutboundAttachment a :
        attachments.findByTenantIdAndMessageId(message.getTenantId(), message.getId()))
      helper.addAttachment(
          a.getFilename(), new ByteArrayResource(attachmentService.bytes(a)), a.getMediaType());
    mime.setSentDate(java.util.Date.from(java.time.Instant.now()));
    mime.saveChanges();
    byte[] sentMime;
    try {
      var output = new ByteArrayOutputStream();
      mime.writeTo(output);
      sentMime = output.toByteArray();
    } catch (java.io.IOException exception) {
      throw new MessagingException("Could not prepare sent message", exception);
    }
    int prepared =
        jdbc.update(
            "insert into mail_sent_copies(message_id,tenant_id,user_id,connection_id,rfc_message_id,mime_bytes) "
                + "values (?,?,?,?,?,?) on conflict (message_id) do nothing",
            message.getId(),
            message.getTenantId(),
            message.getCreatedBy(),
            c.getId(),
            message.getMessageId(),
            sentMime);
    if (prepared != 1)
      throw new ApiException(
          409, "MAIL_SEND_ALREADY_PREPARED", "This email has an existing submission attempt");
    sender.send(mime);
  }

  private static String[] addresses(List<OutboundRecipient> rows, String type) {
    return rows.stream()
        .filter(r -> type.equals(r.getRecipientType()))
        .map(OutboundRecipient::getEmail)
        .toArray(String[]::new);
  }
}

@Component
class OutboundMailDeliveryWorker {
  private static final Logger log = LoggerFactory.getLogger(OutboundMailDeliveryWorker.class);
  private final JdbcTemplate jdbc;
  private final OutboundMessageRepo messages;
  private final OutboundSmtpTransport transport;

  OutboundMailDeliveryWorker(
      JdbcTemplate jdbc, OutboundMessageRepo messages, OutboundSmtpTransport transport) {
    this.jdbc = jdbc;
    this.messages = messages;
    this.transport = transport;
  }

  @Scheduled(fixedDelayString = "${mail.outbox.poll-ms:5000}")
  void deliver() {
    jdbc.update(
        "update mail_send_attempts set status='UNKNOWN',error_code='AMBIGUOUS_OUTCOME',completed_at=now() "
            + "where status='SENDING' and started_at<now()-interval '5 minutes'");
    jdbc.update(
        "update outbound_messages set status='NEEDS_REVIEW',error_code='AMBIGUOUS_OUTCOME',updated_at=now() where status='SENDING' and updated_at<now()-interval '5 minutes'");
    List<UUID> ids =
        jdbc.query(
            "select id from outbound_messages where status='QUEUED' order by queued_at limit 10",
            (ResultSet rs, int row) -> (UUID) rs.getObject(1));
    for (UUID id : ids) {
      if (jdbc.update(
              "update outbound_messages set status='SENDING',send_attempts=send_attempts+1,updated_at=now() where id=? and status='QUEUED'",
              id)
          != 1) continue;
      OutboundMessage message = messages.findById(id).orElse(null);
      if (message == null) continue;
      UUID attemptId = UUID.randomUUID();
      jdbc.update(
          "insert into mail_send_attempts(id,tenant_id,message_id,attempt_number,status) values (?,?,?,?,?)",
          attemptId,
          message.getTenantId(),
          id,
          message.getSendAttempts(),
          "SENDING");
      try {
        transport.send(message);
        jdbc.update(
            "update mail_send_attempts set status='ACCEPTED',completed_at=now() where id=?",
            attemptId);
        jdbc.update(
            "update outbound_messages set status='SENT',sent_at=now(),updated_at=now(),error_code=null where id=? and status='SENDING'",
            id);
        if (message.getQuotationId() != null)
          jdbc.update(
              "update quotations set status='SENT',sent_at=now(),updated_at=now() where id=? and tenant_id=? and status='READY_TO_SEND'",
              message.getQuotationId(),
              message.getTenantId());
      } catch (Exception e) {
        jdbc.update(
            "update mail_send_attempts set status='UNKNOWN',error_code='SMTP_OUTCOME_UNCERTAIN',completed_at=now() where id=?",
            attemptId);
        log.warn(
            "Outbound mail {} requires review after SMTP failure ({})",
            id,
            e.getClass().getSimpleName());
        jdbc.update(
            "update outbound_messages set status='NEEDS_REVIEW',error_code='SMTP_OUTCOME_UNCERTAIN',updated_at=now() where id=? and status='SENDING'",
            id);
      }
    }
  }
}
