package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class ImapMailProvider implements MailInboxProvider {
  private final EmailMessageRepo messages;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;
  private final ImapMessageImportService importer;

  ImapMailProvider(
      EmailMessageRepo messages,
      MailCredentialCipher cipher,
      MailTransportSupport transport,
      ImapMessageImportService importer) {
    this.messages = messages;
    this.cipher = cipher;
    this.transport = transport;
    this.importer = importer;
  }

  public String providerType() {
    return "IMAP_SMTP";
  }

  public MailSyncResult sync(MailConnection connection, AppUser user) {
    transport.validate(
        connection.getImapHost(), connection.getImapPort(), connection.getImapSecurityMode());
    Store store = null;
    Folder folder = null;
    try {
      Session session =
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
      folder = store.getFolder("INBOX");
      folder.open(Folder.READ_ONLY);
      if (!(folder instanceof UIDFolder uidFolder))
        throw new ApiException(502, "IMAP_UID_UNAVAILABLE", "Mailbox does not support stable UIDs");
      long validity = uidFolder.getUIDValidity();
      if (validity <= 0)
        throw new ApiException(502, "IMAP_UID_UNAVAILABLE", "Mailbox UID validity is unavailable");
      if (!Long.valueOf(validity).equals(connection.getImapInboxUidValidity())) {
        connection.setImapInboxUidValidity(validity);
        connection.setImapInboxLastUid(0L);
      }
      reconcileInbox(connection, folder, uidFolder, validity);
      long after = connection.getImapInboxLastUid() == null ? 0L : connection.getImapInboxLastUid();
      long next = uidFolder.getUIDNext();
      long upper = Math.min(after + 10_000, next > 0 ? next - 1 : after + 10_000);
      if (upper <= after) return new MailSyncResult(0, 0, 0, 0);
      Message[] newMessages = uidFolder.getMessagesByUID(after + 1, upper);
      if (newMessages == null || newMessages.length == 0) {
        connection.setImapInboxLastUid(upper);
        return new MailSyncResult(0, 0, 0, 0);
      }
      int length = Math.min(100, newMessages.length);
      Message[] batch = Arrays.copyOf(newMessages, length);
      FetchProfile profile = new FetchProfile();
      profile.add(FetchProfile.Item.ENVELOPE);
      profile.add(FetchProfile.Item.FLAGS);
      folder.fetch(batch, profile);
      int inserted = 0, skipped = 0, failed = 0;
      for (Message message : batch) {
        try {
          long imapUid = uidFolder.getUID(message);
          if (message.isSet(Flags.Flag.DELETED)) {
            skipped++;
            connection.setImapInboxLastUid(imapUid);
            continue;
          }
          String id = providerId(message, folder);
          if (messages.existsByConnectionIdAndImapFolderAndImapUidValidityAndImapUid(
                  connection.getId(), "INBOX", validity, imapUid)
              || messages.existsByConnectionIdAndProviderMessageId(connection.getId(), id)) {
            skipped++;
            connection.setImapInboxLastUid(imapUid);
            continue;
          }
          String legacyId = legacyProviderId(header(message, "Message-ID"));
          if (legacyId != null) {
            var legacy =
                messages.findByTenantIdAndConnectionIdAndProviderMessageId(
                    connection.getTenantId(), connection.getId(), legacyId);
            if (legacy.isPresent()) {
              EmailMessage row = legacy.get();
              row.setImapUid(imapUid);
              row.setImapUidValidity(validity);
              row.setImapFolder("INBOX");
              row.setInInbox(true);
              row.setRead(message.isSet(Flags.Flag.SEEN));
              row.setRfcMessageId(header(message, "Message-ID"));
              row.setInReplyTo(header(message, "In-Reply-To"));
              row.setReferenceHeaders(header(message, "References"));
              messages.saveAndFlush(row);
              skipped++;
              connection.setImapInboxLastUid(imapUid);
              continue;
            }
          }
          EmailMessage row = parse(message, connection, id);
          row.setImapUid(imapUid);
          row.setImapUidValidity(validity);
          row.setImapFolder("INBOX");
          importer.importMessage(row, message);
          inserted++;
          connection.setImapInboxLastUid(imapUid);
        } catch (DataIntegrityViolationException e) {
          skipped++;
        } catch (Exception e) {
          failed++;
          break;
        }
      }
      if (failed == 0 && batch.length == newMessages.length) connection.setImapInboxLastUid(upper);
      return new MailSyncResult(batch.length, inserted, skipped, failed);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          502, "IMAP_SYNC_FAILED", "Could not sync the mailbox. Check its connection settings.");
    } finally {
      try {
        if (folder != null && folder.isOpen()) folder.close(false);
      } catch (Exception ignored) {
      }
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void reconcileInbox(
      MailConnection connection, Folder folder, UIDFolder uidFolder, long validity)
      throws Exception {
    Message[] current = folder.getMessages();
    if (current.length > 0) {
      FetchProfile profile = new FetchProfile();
      profile.add(FetchProfile.Item.FLAGS);
      folder.fetch(current, profile);
    }
    Map<Long, Boolean> present = new HashMap<>();
    for (Message message : current) {
      if (!message.isSet(Flags.Flag.DELETED)) {
        long uid = uidFolder.getUID(message);
        if (uid > 0) present.put(uid, message.isSet(Flags.Flag.SEEN));
      }
    }
    var changed = new ArrayList<EmailMessage>();
    for (EmailMessage row :
        messages.findImapMessagesForReconciliation(
            connection.getTenantId(), connection.getId(), validity)) {
      Boolean read = present.get(row.getImapUid());
      boolean inInbox = read != null;
      if (row.isInInbox() != inInbox || (inInbox && row.isRead() != read)) {
        row.setInInbox(inInbox);
        if (inInbox) row.setRead(read);
        changed.add(row);
      }
    }
    if (!changed.isEmpty()) messages.saveAll(changed);
  }

  private static EmailMessage parse(Message m, MailConnection c, String id) throws Exception {
    EmailMessage row = new EmailMessage();
    row.setTenantId(c.getTenantId());
    row.setConnectionId(c.getId());
    row.setProviderMessageId(id);
    row.setRfcMessageId(header(m, "Message-ID"));
    row.setInReplyTo(header(m, "In-Reply-To"));
    row.setReferenceHeaders(header(m, "References"));
    Address[] from = m.getFrom();
    InternetAddress sender =
        from != null && from.length > 0 && from[0] instanceof InternetAddress a ? a : null;
    row.setFromEmail(sender == null ? "" : sender.getAddress());
    row.setFromName(sender == null ? "" : decode(sender.getPersonal()));
    Address[] replyTo = m.getReplyTo();
    row.setReplyToEmail(replyTo == null ? "" : InternetAddress.toString(replyTo));
    Address[] to = m.getRecipients(Message.RecipientType.TO);
    row.setToEmail(to == null ? "" : InternetAddress.toString(to));
    Address[] cc = m.getRecipients(Message.RecipientType.CC);
    row.setCcEmail(cc == null ? "" : InternetAddress.toString(cc));
    row.setSubject(decode(m.getSubject()));
    row.setBodyText(body(m));
    Date date = m.getReceivedDate();
    if (date == null) date = m.getSentDate();
    row.setReceivedAt(date == null ? Instant.now() : date.toInstant());
    row.setRead(m.isSet(Flags.Flag.SEEN));
    return row;
  }

  private static String body(Part part) throws Exception {
    if (part.isMimeType("text/plain") && part.getFileName() == null)
      return Objects.toString(part.getContent(), "");
    if (part.isMimeType("text/html") && part.getFileName() == null)
      return cleanHtml(Objects.toString(part.getContent(), ""));
    if (part.isMimeType("multipart/*")) {
      Object content = part.getContent();
      if (content instanceof Multipart multi) {
        String html = "";
        for (int i = 0; i < multi.getCount(); i++) {
          BodyPart child = multi.getBodyPart(i);
          if (child.isMimeType("text/plain") && child.getFileName() == null) return body(child);
          if (html.isBlank() && child.isMimeType("text/html") && child.getFileName() == null)
            html = body(child);
          if (child.isMimeType("multipart/*")) {
            String nested = body(child);
            if (!nested.isBlank()) return nested;
          }
        }
        return html;
      }
    }
    return "";
  }

  private static String cleanHtml(String html) {
    return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
        .replaceAll("(?i)<br\\s*/?>|</p>", "\n")
        .replaceAll("<[^>]+>", " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim();
  }

  private static String decode(String value) {
    if (value == null) return "";
    try {
      return MimeUtility.decodeText(value);
    } catch (Exception e) {
      return value;
    }
  }

  private static String header(Message message, String name) throws Exception {
    String[] values = message.getHeader(name);
    return values == null || values.length == 0 ? null : values[0];
  }

  private static String providerId(Message m, Folder folder) throws Exception {
    if (folder instanceof UIDFolder uid) {
      long n = uid.getUID(m);
      if (n > 0) return "uid:INBOX:" + uid.getUIDValidity() + ":" + n;
    }
    String[] headers = m.getHeader("Message-ID");
    if (headers != null && headers.length > 0 && !headers[0].isBlank())
      return legacyProviderId(headers[0]);
    String seed =
        Objects.toString(m.getSentDate(), "")
            + Objects.toString(m.getReceivedDate(), "")
            + Objects.toString(m.getSubject(), "")
            + Arrays.toString(m.getFrom());
    return "fallback:" + sha(seed);
  }

  static String legacyProviderId(String rfcMessageId) throws Exception {
    if (rfcMessageId == null || rfcMessageId.isBlank()) return null;
    return "mid:" + sha(rfcMessageId.trim().toLowerCase(Locale.ROOT));
  }

  private static String sha(String input) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
  }
}
