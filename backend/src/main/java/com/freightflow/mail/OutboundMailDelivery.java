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
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
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
  private static final Logger log = LoggerFactory.getLogger(OutboundSmtpTransport.class);
  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;
  private final OutboundRecipientRepo recipients;
  private final OutboundAttachmentRepo attachments;
  private final MailAttachmentService attachmentService;

  OutboundSmtpTransport(
      MailConnectionRepo connections,
      MailCredentialCipher cipher,
      MailTransportSupport transport,
      OutboundRecipientRepo recipients,
      OutboundAttachmentRepo attachments,
      MailAttachmentService attachmentService) {
    this.connections = connections;
    this.cipher = cipher;
    this.transport = transport;
    this.recipients = recipients;
    this.attachments = attachments;
    this.attachmentService = attachmentService;
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
    sender.send(mime);
    appendToSent(c, mime);
  }

  private void appendToSent(MailConnection connection, MimeMessage mime) {
    Store store = null;
    try {
      var session =
          transport.session(
              "imap",
              connection.getImapHost(),
              connection.getImapPort(),
              connection.getImapSecurityMode());
      store = session.getStore("imap");
      store.connect(
          connection.getImapHost(),
          connection.getImapPort(),
          connection.getImapUsername(),
          cipher.decrypt(connection.getImapPasswordEncrypted()));
      Folder sent = store.getFolder("Sent");
      if (!sent.exists()) sent = store.getFolder("Sent Items");
      if (sent.exists()) sent.appendMessages(new Message[] {mime});
    } catch (Exception e) {
      // SMTP has already accepted the message. APPEND failure must not trigger a second send.
      log.warn(
          "Sent-folder append unavailable for mailbox {} ({})",
          connection.getId(),
          e.getClass().getSimpleName());
    } finally {
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {
      }
    }
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
