package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.MessageIDTerm;
import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SentMailboxSync {
  record Outcome(String folder, boolean complete) {}

  private static final List<String> SENT_NAMES =
      List.of(
          "sent",
          "sent items",
          "sent mail",
          "sent messages",
          "gesendet",
          "gesendete elemente",
          "envoyes",
          "elements envoyes",
          "enviados",
          "enviada",
          "posta inviata",
          "inviati");

  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;

  SentMailboxSync(
      MailConnectionRepo connections, MailCredentialCipher cipher, MailTransportSupport transport) {
    this.connections = connections;
    this.cipher = cipher;
    this.transport = transport;
  }

  Outcome sync(SentCopySyncWorker.Copy copy) throws Exception {
    MailConnection connection =
        connections
            .findByIdAndTenantIdAndUserId(copy.connectionId(), copy.tenantId(), copy.userId())
            .orElseThrow(
                () ->
                    new ApiException(
                        409, "MAIL_CONNECTION_INVALID", "Original mailbox unavailable"));
    if (!"IMAP_SMTP".equals(connection.getProvider())
        || !connection.isActive()
        || !"CONNECTED".equals(connection.getStatus()))
      throw new ApiException(409, "MAIL_CONNECTION_INVALID", "Original mailbox unavailable");
    transport.validate(
        connection.getImapHost(), connection.getImapPort(), connection.getImapSecurityMode());
    var session =
        transport.session(
            "imap",
            connection.getImapHost(),
            connection.getImapPort(),
            connection.getImapSecurityMode());
    Store store = session.getStore("imap");
    try {
      store.connect(
          connection.getImapHost(),
          connection.getImapPort(),
          connection.getImapUsername(),
          cipher.decrypt(connection.getImapPasswordEncrypted()));
      Folder sent = sentFolder(store.getDefaultFolder().list("*"));
      if (sent == null)
        throw new ApiException(502, "MAIL_SENT_FOLDER_MISSING", "Sent mailbox was not found");
      sent.open(Folder.READ_ONLY);
      try {
        if (containsMessageId(sent, copy.rfcMessageId()))
          return new Outcome(sent.getFullName(), true);
      } finally {
        sent.close(false);
      }
      if (providerStoresSent(connection)) return new Outcome(sent.getFullName(), false);
      MimeMessage mime = new MimeMessage(session, new ByteArrayInputStream(copy.mimeBytes()));
      sent.appendMessages(new Message[] {mime});
      return new Outcome(sent.getFullName(), true);
    } finally {
      if (store.isConnected()) store.close();
    }
  }

  static boolean providerStoresSent(MailConnection connection) {
    String smtp = String.valueOf(connection.getSmtpHost()).toLowerCase(Locale.ROOT);
    String imap = String.valueOf(connection.getImapHost()).toLowerCase(Locale.ROOT);
    return ("smtp.gmail.com".equals(smtp) && "imap.gmail.com".equals(imap))
        || ("smtp.office365.com".equals(smtp) && "outlook.office365.com".equals(imap));
  }

  static Folder sentFolder(Folder[] folders) throws Exception {
    for (Folder folder : folders) {
      if (folder instanceof IMAPFolder imap) {
        try {
          String[] attributes = imap.getAttributes();
          if (attributes != null && Arrays.stream(attributes).anyMatch("\\Sent"::equalsIgnoreCase))
            return folder;
        } catch (jakarta.mail.MessagingException ignored) {
          // Fall back to a known folder name when SPECIAL-USE is unavailable.
        }
      }
    }
    for (Folder folder : folders) {
      String normalized = normalize(folder.getName());
      if (SENT_NAMES.contains(normalized)) return folder;
    }
    return null;
  }

  private static String normalize(String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private static boolean containsMessageId(Folder folder, String messageId) throws Exception {
    try {
      for (Message message : folder.search(new MessageIDTerm(messageId))) {
        if (matchesMessageId(message, messageId)) return true;
      }
    } catch (jakarta.mail.MessagingException ignored) {
      // Some IMAP servers cannot SEARCH Message-ID; inspect recent headers instead.
    }
    int count = folder.getMessageCount();
    if (count > 0)
      for (Message message : folder.getMessages(Math.max(1, count - 199), count))
        if (matchesMessageId(message, messageId)) return true;
    return false;
  }

  private static boolean matchesMessageId(Message message, String messageId) throws Exception {
    String[] values = message.getHeader("Message-ID");
    return values != null
        && Arrays.stream(values).anyMatch(value -> messageId.equalsIgnoreCase(value.trim()));
  }
}

@Component
class SentCopySyncWorker {
  private static final Logger log = LoggerFactory.getLogger(SentCopySyncWorker.class);

  record Copy(
      UUID messageId,
      UUID tenantId,
      UUID userId,
      UUID connectionId,
      String rfcMessageId,
      byte[] mimeBytes,
      int attempts) {}

  private final JdbcTemplate jdbc;
  private final SentMailboxSync synchronizer;

  SentCopySyncWorker(JdbcTemplate jdbc, SentMailboxSync synchronizer) {
    this.jdbc = jdbc;
    this.synchronizer = synchronizer;
  }

  @Scheduled(fixedDelayString = "${mail.sent-copy.poll-ms:15000}")
  void sync() {
    jdbc.update(
        "update mail_sent_copies set status='RETRY',updated_at=now() "
            + "where status='SYNCING' and updated_at<now()-interval '5 minutes'");
    List<Copy> copies =
        jdbc.query(
            "select sc.message_id,sc.tenant_id,sc.user_id,sc.connection_id,sc.rfc_message_id,sc.mime_bytes,sc.attempts "
                + "from mail_sent_copies sc join outbound_messages m on m.id=sc.message_id "
                + "and m.tenant_id=sc.tenant_id and m.created_by=sc.user_id and m.connection_id=sc.connection_id "
                + "where m.status='SENT' and m.sent_at<now()-interval '30 seconds' "
                + "and sc.status in ('PREPARED','RETRY') and sc.next_attempt_at<=now() "
                + "order by sc.next_attempt_at limit 10",
            (rs, row) ->
                new Copy(
                    rs.getObject("message_id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("connection_id", UUID.class),
                    rs.getString("rfc_message_id"),
                    rs.getBytes("mime_bytes"),
                    rs.getInt("attempts")));
    for (Copy copy : copies) {
      if (jdbc.update(
              "update mail_sent_copies set status='SYNCING',attempts=attempts+1,updated_at=now() "
                  + "where message_id=? and tenant_id=? and status in ('PREPARED','RETRY')",
              copy.messageId(),
              copy.tenantId())
          != 1) continue;
      try {
        SentMailboxSync.Outcome result = synchronizer.sync(copy);
        if (result.complete())
          jdbc.update(
              "update mail_sent_copies set status='SYNCED',folder_name=?,mime_bytes=null,synced_at=now(),error_code=null,updated_at=now() "
                  + "where message_id=? and tenant_id=? and status='SYNCING'",
              result.folder(),
              copy.messageId(),
              copy.tenantId());
        else retry(copy, "PROVIDER_COPY_PENDING", 300);
      } catch (Exception exception) {
        log.warn(
            "Sent-folder sync pending for message {} ({})",
            copy.messageId(),
            exception.getClass().getSimpleName());
        String code = exception instanceof ApiException api ? api.code : "SENT_COPY_SYNC_FAILED";
        retry(copy, code, Math.min(3600, 30L << Math.min(copy.attempts(), 6)));
      }
    }
  }

  private void retry(Copy copy, String code, long seconds) {
    jdbc.update(
        "update mail_sent_copies set status='RETRY',error_code=?,next_attempt_at=now()+(? * interval '1 second'),updated_at=now() "
            + "where message_id=? and tenant_id=? and status='SYNCING'",
        code,
        seconds,
        copy.messageId(),
        copy.tenantId());
  }
}
