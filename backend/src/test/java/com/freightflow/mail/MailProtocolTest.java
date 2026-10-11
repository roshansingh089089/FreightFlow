package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.entity.OutboundAttachment;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.entity.OutboundRecipient;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.mail.repository.OutboundAttachmentRepo;
import com.freightflow.mail.repository.OutboundRecipientRepo;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailProtocolTest {
  private GreenMail server;
  private MailCredentialCipher cipher;
  private MailTransportSupport support;
  private MailConnection connection;

  @BeforeEach
  void start() {
    server =
        new GreenMail(
            new ServerSetup[] {
              new ServerSetup(0, "127.0.0.1", "smtp"), new ServerSetup(0, "127.0.0.1", "imap")
            });
    server.start();
    server.setUser("agent@example.test", "agent", "secret");
    server.setUser("buyer@example.test", "buyer", "secret");
    server.setUser("manager@example.test", "manager", "secret");
    cipher = new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    support = mock(MailTransportSupport.class);
    Properties imap = new Properties();
    imap.setProperty("mail.imap.host", "localhost");
    imap.setProperty("mail.imap.port", Integer.toString(server.getImap().getPort()));
    when(support.session("imap", "localhost", server.getImap().getPort(), "STARTTLS"))
        .thenReturn(Session.getInstance(imap));
    Properties smtp = new Properties();
    smtp.setProperty("mail.smtp.host", "localhost");
    smtp.setProperty("mail.smtp.port", Integer.toString(server.getSmtp().getPort()));
    smtp.setProperty("mail.smtp.auth", "true");
    when(support.session("smtp", "localhost", server.getSmtp().getPort(), "STARTTLS"))
        .thenReturn(Session.getInstance(smtp));
    connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(UUID.randomUUID());
    connection.setUserId(UUID.randomUUID());
    connection.setProvider("IMAP_SMTP");
    connection.setActive(true);
    connection.setStatus("CONNECTED");
    connection.setEmailAddress("agent@example.test");
    connection.setImapHost("localhost");
    connection.setImapPort(server.getImap().getPort());
    connection.setImapUsername("agent");
    connection.setImapPasswordEncrypted(cipher.encrypt("secret"));
    connection.setImapSecurityMode("STARTTLS");
    connection.setSmtpHost("localhost");
    connection.setSmtpPort(server.getSmtp().getPort());
    connection.setSmtpUsername("agent");
    connection.setSmtpPasswordEncrypted(cipher.encrypt("secret"));
    connection.setSmtpSecurityMode("STARTTLS");
    connection.setSmtpFrom("agent@example.test");
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  @Test
  void imapReadsRfcHeadersAndStableUid() throws Exception {
    Properties smtp = new Properties();
    smtp.setProperty("mail.smtp.host", "localhost");
    smtp.setProperty("mail.smtp.port", Integer.toString(server.getSmtp().getPort()));
    MimeMessage incoming = new MimeMessage(Session.getInstance(smtp));
    incoming.setFrom(new InternetAddress("buyer@example.test"));
    incoming.setRecipients(Message.RecipientType.TO, "agent@example.test");
    incoming.setSubject("Re: Freight enquiry", "UTF-8");
    incoming.setText("Please revise the quotation", "UTF-8");
    incoming.setHeader("In-Reply-To", "<quote-1@example.test>");
    incoming.setHeader("References", "<enquiry-1@example.test> <quote-1@example.test>");
    Transport.send(incoming);
    assertTrue(server.waitForIncomingEmail(5000, 1));

    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    ImapMessageImportService importer = mock(ImapMessageImportService.class);
    ImapMailProvider provider = new ImapMailProvider(messages, cipher, support, importer);
    AppUser user = new AppUser();
    user.setId(connection.getUserId());
    user.setTenantId(connection.getTenantId());
    MailSyncResult result = provider.sync(connection, user);
    assertEquals(1, result.inserted());
    var captured = org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
    verify(importer).importMessage(captured.capture(), any(Message.class));
    EmailMessage stored = captured.getValue();
    assertEquals("<quote-1@example.test>", stored.getInReplyTo());
    assertTrue(stored.getReferenceHeaders().contains("<enquiry-1@example.test>"));
    assertEquals("buyer@example.test", stored.getFromEmail());
    assertTrue(stored.getImapUid() > 0);
    assertTrue(stored.getImapUidValidity() > 0);
    assertEquals(0, provider.sync(connection, user).inserted());
  }

  @Test
  void imapBackfillsLegacyMessageInsteadOfImportingItAgain() throws Exception {
    Properties smtp = new Properties();
    smtp.setProperty("mail.smtp.host", "localhost");
    smtp.setProperty("mail.smtp.port", Integer.toString(server.getSmtp().getPort()));
    MimeMessage incoming = new MimeMessage(Session.getInstance(smtp));
    incoming.setFrom(new InternetAddress("buyer@example.test"));
    incoming.setRecipients(Message.RecipientType.TO, "agent@example.test");
    incoming.setSubject("Freight enquiry", "UTF-8");
    incoming.setText("Please quote", "UTF-8");
    Transport.send(incoming);
    assertTrue(server.waitForIncomingEmail(5000, 1));

    String rfcId = server.getReceivedMessages()[0].getHeader("Message-ID", null);
    EmailMessage legacy = new EmailMessage();
    legacy.setTenantId(connection.getTenantId());
    legacy.setConnectionId(connection.getId());
    legacy.setProviderMessageId(ImapMailProvider.legacyProviderId(rfcId));
    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    when(messages.findByTenantIdAndConnectionIdAndProviderMessageId(
            connection.getTenantId(), connection.getId(), legacy.getProviderMessageId()))
        .thenReturn(Optional.of(legacy));
    ImapMessageImportService importer = mock(ImapMessageImportService.class);
    ImapMailProvider provider = new ImapMailProvider(messages, cipher, support, importer);
    AppUser user = new AppUser();
    user.setId(connection.getUserId());
    user.setTenantId(connection.getTenantId());

    MailSyncResult result = provider.sync(connection, user);

    assertEquals(0, result.inserted());
    assertEquals(1, result.skippedDuplicates());
    assertEquals(rfcId, legacy.getRfcMessageId());
    assertTrue(legacy.getImapUid() > 0);
    verify(messages).saveAndFlush(legacy);
    verifyNoInteractions(importer);
  }

  @Test
  void repeatedAndRestartedSyncKeepsFourDistinctUidsWithSameSubject() throws Exception {
    Properties smtp = new Properties();
    smtp.setProperty("mail.smtp.host", "localhost");
    smtp.setProperty("mail.smtp.port", Integer.toString(server.getSmtp().getPort()));
    for (int index = 0; index < 4; index++) {
      MimeMessage incoming = new MimeMessage(Session.getInstance(smtp));
      incoming.setFrom(new InternetAddress("buyer@example.test"));
      incoming.setRecipients(Message.RecipientType.TO, "agent@example.test");
      incoming.setSubject("Same freight subject", "UTF-8");
      incoming.setText("Request " + index, "UTF-8");
      Transport.send(incoming);
    }
    assertTrue(server.waitForIncomingEmail(5000, 4));
    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    ImapMessageImportService importer = mock(ImapMessageImportService.class);
    Set<Long> storedUids = new HashSet<>();
    when(messages.existsByConnectionIdAndImapFolderAndImapUidValidityAndImapUid(
            eq(connection.getId()), eq("INBOX"), anyLong(), anyLong()))
        .thenAnswer(call -> storedUids.contains(call.getArgument(3)));
    doAnswer(
            call -> {
              EmailMessage row = call.getArgument(0);
              storedUids.add(row.getImapUid());
              assertEquals("INBOX", row.getImapFolder());
              return null;
            })
        .when(importer)
        .importMessage(any(EmailMessage.class), any(Message.class));
    AppUser user = new AppUser();
    user.setId(connection.getUserId());
    user.setTenantId(connection.getTenantId());

    assertEquals(
        4,
        new ImapMailProvider(messages, cipher, support, importer)
            .sync(connection, user)
            .inserted());
    assertEquals(4, storedUids.size());
    connection.setImapInboxLastUid(0L);
    MailSyncResult repeated =
        new ImapMailProvider(messages, cipher, support, importer).sync(connection, user);
    assertEquals(0, repeated.inserted());
    assertEquals(4, repeated.skippedDuplicates());
    assertEquals(4, storedUids.size());
    verify(importer, times(4)).importMessage(any(EmailMessage.class), any(Message.class));
  }

  @Test
  void repeatedSyncReconcilesDeletedInboxMessageWithoutDeletingHistory() throws Exception {
    Properties smtp = new Properties();
    smtp.setProperty("mail.smtp.host", "localhost");
    smtp.setProperty("mail.smtp.port", Integer.toString(server.getSmtp().getPort()));
    MimeMessage incoming = new MimeMessage(Session.getInstance(smtp));
    incoming.setFrom(new InternetAddress("buyer@example.test"));
    incoming.setRecipients(Message.RecipientType.TO, "agent@example.test");
    incoming.setSubject("Moved freight enquiry", "UTF-8");
    incoming.setText("Please quote", "UTF-8");
    Transport.send(incoming);
    assertTrue(server.waitForIncomingEmail(5000, 1));

    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    ImapMessageImportService importer = mock(ImapMessageImportService.class);
    ImapMailProvider provider = new ImapMailProvider(messages, cipher, support, importer);
    AppUser user = new AppUser();
    user.setId(connection.getUserId());
    user.setTenantId(connection.getTenantId());
    assertEquals(1, provider.sync(connection, user).inserted());
    var captured = org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
    verify(importer).importMessage(captured.capture(), any(Message.class));
    EmailMessage stored = captured.getValue();
    when(messages.findImapMessagesForReconciliation(
            connection.getTenantId(), connection.getId(), connection.getImapInboxUidValidity()))
        .thenReturn(List.of(stored));

    Store store = Session.getInstance(new Properties()).getStore("imap");
    store.connect("localhost", server.getImap().getPort(), "agent", "secret");
    Folder inbox = store.getFolder("INBOX");
    inbox.open(Folder.READ_WRITE);
    inbox.getMessage(1).setFlag(Flags.Flag.DELETED, true);
    inbox.close(true);
    store.close();

    assertEquals(0, provider.sync(connection, user).inserted());
    assertFalse(stored.isInInbox());
    verify(messages).saveAll(List.of(stored));
  }

  @Test
  void smtpSubmitsReplyHeadersRecipientsAndAttachment() throws Exception {
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    OutboundRecipientRepo recipients = mock(OutboundRecipientRepo.class);
    OutboundAttachmentRepo attachments = mock(OutboundAttachmentRepo.class);
    MailAttachmentService attachmentService = mock(MailAttachmentService.class);
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(connection.getTenantId());
    message.setCreatedBy(connection.getUserId());
    message.setConnectionId(connection.getId());
    message.setMessageId("<reply-1@example.test>");
    message.setInReplyTo("<enquiry-1@example.test>");
    message.setReferenceHeaders("<enquiry-1@example.test>");
    message.setSubject("Re: Freight enquiry");
    message.setBodyText("Please see our quotation.");
    when(connections.findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId()))
        .thenReturn(java.util.Optional.of(connection));
    when(recipients.findByTenantIdAndMessageId(connection.getTenantId(), message.getId()))
        .thenReturn(
            List.of(
                recipient(message, "TO", "buyer@example.test"),
                recipient(message, "CC", "manager@example.test")));
    OutboundAttachment pdf = new OutboundAttachment();
    pdf.setFilename("quotation.pdf");
    pdf.setMediaType("application/pdf");
    when(attachments.findByTenantIdAndMessageId(connection.getTenantId(), message.getId()))
        .thenReturn(List.of(pdf));
    when(attachmentService.bytes(pdf)).thenReturn("%PDF-1.4\n".getBytes());
    OutboundSmtpTransport transport =
        new OutboundSmtpTransport(
            connections, cipher, support, recipients, attachments, attachmentService);
    transport.send(message);
    assertTrue(server.waitForIncomingEmail(5000, 2));
    MimeMessage delivered = server.getReceivedMessages()[0];
    assertEquals("<reply-1@example.test>", delivered.getHeader("Message-ID", null));
    assertEquals("<enquiry-1@example.test>", delivered.getHeader("In-Reply-To", null));
    assertEquals("<enquiry-1@example.test>", delivered.getHeader("References", null));
    assertEquals("Re: Freight enquiry", delivered.getSubject());
    assertTrue(delivered.isMimeType("multipart/*"));
    assertEquals(
        "manager@example.test",
        ((InternetAddress) delivered.getRecipients(Message.RecipientType.CC)[0]).getAddress());
    Multipart content = (Multipart) delivered.getContent();
    assertEquals("quotation.pdf", content.getBodyPart(1).getFileName());
  }

  @Test
  void smtpSendsExplicitReplyWithoutUnavailableThreadingHeaders() throws Exception {
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    OutboundRecipientRepo recipients = mock(OutboundRecipientRepo.class);
    OutboundAttachmentRepo attachments = mock(OutboundAttachmentRepo.class);
    MailAttachmentService attachmentService = mock(MailAttachmentService.class);
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(connection.getTenantId());
    message.setCreatedBy(connection.getUserId());
    message.setConnectionId(connection.getId());
    message.setMessageId("<new-reply@example.test>");
    message.setSubject("Re: Freight enquiry");
    message.setBodyText("Our reply");
    when(connections.findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId()))
        .thenReturn(java.util.Optional.of(connection));
    when(recipients.findByTenantIdAndMessageId(connection.getTenantId(), message.getId()))
        .thenReturn(List.of(recipient(message, "TO", "buyer@example.test")));
    when(attachments.findByTenantIdAndMessageId(connection.getTenantId(), message.getId()))
        .thenReturn(List.of());
    OutboundSmtpTransport transport =
        new OutboundSmtpTransport(
            connections, cipher, support, recipients, attachments, attachmentService);
    transport.send(message);
    assertTrue(server.waitForIncomingEmail(5000, 1));
    MimeMessage delivered = server.getReceivedMessages()[0];
    assertEquals("<new-reply@example.test>", delivered.getHeader("Message-ID", null));
    assertNull(delivered.getHeader("In-Reply-To", null));
    assertNull(delivered.getHeader("References", null));
    assertEquals("Re: Freight enquiry", delivered.getSubject());
  }

  private static OutboundRecipient recipient(OutboundMessage message, String type, String email) {
    OutboundRecipient row = new OutboundRecipient();
    row.setTenantId(message.getTenantId());
    row.setMessageId(message.getId());
    row.setRecipientType(type);
    row.setEmail(email);
    return row;
  }
}
