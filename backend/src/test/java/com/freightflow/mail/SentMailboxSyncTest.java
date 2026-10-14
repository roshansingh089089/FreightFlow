package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SentMailboxSyncTest {
  private GreenMail server;

  @AfterEach
  void stop() {
    if (server != null) server.stop();
  }

  @Test
  void appendsOnceAndReconcilesByMessageIdOnRetry() throws Exception {
    server = new GreenMail(new ServerSetup(0, "127.0.0.1", "imap"));
    server.start();
    server.setUser("agent@example.test", "agent", "secret");
    MailConnection connection = connection();
    MailConnectionRepo repo = mock(MailConnectionRepo.class);
    when(repo.findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId()))
        .thenReturn(Optional.of(connection));
    MailTransportSupport transport = mock(MailTransportSupport.class);
    Properties properties = new Properties();
    properties.setProperty("mail.imap.host", "localhost");
    properties.setProperty("mail.imap.port", Integer.toString(server.getImap().getPort()));
    when(transport.session("imap", "localhost", server.getImap().getPort(), "STARTTLS"))
        .thenReturn(Session.getInstance(properties));
    var sync =
        new SentMailboxSync(
            repo,
            new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            transport);
    Store store = Session.getInstance(properties).getStore("imap");
    store.connect("localhost", server.getImap().getPort(), "agent", "secret");
    assertTrue(store.getFolder("Sent").create(Folder.HOLDS_MESSAGES));
    store.close();
    var copy = copy(connection);
    assertTrue(sync.sync(copy).complete());
    connection.setActive(false);
    assertThrows(ApiException.class, () -> sync.sync(copy));
    connection.setActive(true);
    assertTrue(sync.sync(copy).complete());
    store = Session.getInstance(properties).getStore("imap");
    store.connect("localhost", server.getImap().getPort(), "agent", "secret");
    Folder sent = store.getFolder("Sent");
    sent.open(Folder.READ_ONLY);
    assertEquals(1, sent.getMessageCount());
    assertEquals(copy.rfcMessageId(), sent.getMessage(1).getHeader("Message-ID")[0]);
    sent.close(false);
    store.close();
  }

  @Test
  void lostAppendAcknowledgementFindsOlderCopyWhenSearchFailsAnd250NewerMessagesExist()
      throws Exception {
    Folder sent = mock(Folder.class);
    Message original = mock(Message.class);
    Message[] first = new Message[200];
    first[0] = original;
    for (int i = 1; i < first.length; i++) first[i] = mock(Message.class);
    Message[] newer = new Message[51];
    for (int i = 0; i < newer.length; i++) newer[i] = mock(Message.class);
    when(sent.search(any(jakarta.mail.search.SearchTerm.class)))
        .thenThrow(new jakarta.mail.MessagingException("SEARCH unsupported"));
    when(sent.getMessageCount()).thenReturn(251);
    when(sent.getMessages(1, 200)).thenReturn(first);
    when(sent.getMessages(201, 251)).thenReturn(newer);
    when(original.getHeader("Message-ID")).thenReturn(new String[] {"<accepted@example.test>"});

    // The earlier APPEND succeeded; retry must detect it outside the newest 200 messages.
    assertTrue(SentMailboxSync.containsMessageId(sent, "<accepted@example.test>"));
    verify(sent).getMessages(1, 200);
  }

  @Test
  void incompleteProviderScanCannotAuthorizeAnotherAppend() throws Exception {
    Folder sent = mock(Folder.class);
    when(sent.search(any(jakarta.mail.search.SearchTerm.class)))
        .thenThrow(new jakarta.mail.MessagingException("SEARCH unavailable"));
    when(sent.getMessageCount()).thenReturn(251);
    when(sent.getMessages(1, 200))
        .thenThrow(new jakarta.mail.MessagingException("Cannot inspect older headers"));
    assertThrows(
        jakarta.mail.MessagingException.class,
        () -> SentMailboxSync.containsMessageId(sent, "<accepted@example.test>"));
  }

  @Test
  void uncertainPreviousAppendNeverCreatesAnotherCopyWhenLookupFindsNoMatch() throws Exception {
    server = new GreenMail(new ServerSetup(0, "127.0.0.1", "imap"));
    server.start();
    server.setUser("agent@example.test", "agent", "secret");
    MailConnection connection = connection();
    MailConnectionRepo repo = mock(MailConnectionRepo.class);
    when(repo.findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId()))
        .thenReturn(Optional.of(connection));
    MailTransportSupport transport = mock(MailTransportSupport.class);
    Properties properties = new Properties();
    properties.setProperty("mail.imap.host", "localhost");
    properties.setProperty("mail.imap.port", Integer.toString(server.getImap().getPort()));
    when(transport.session("imap", "localhost", server.getImap().getPort(), "STARTTLS"))
        .thenReturn(Session.getInstance(properties));
    Store store = Session.getInstance(properties).getStore("imap");
    store.connect("localhost", server.getImap().getPort(), "agent", "secret");
    assertTrue(store.getFolder("Sent").create(Folder.HOLDS_MESSAGES));
    store.close();
    var initial = copy(connection);
    var retry =
        new SentCopySyncWorker.Copy(
            initial.messageId(),
            initial.tenantId(),
            initial.userId(),
            initial.connectionId(),
            initial.rfcMessageId(),
            initial.mimeBytes(),
            1);
    var sync =
        new SentMailboxSync(
            repo,
            new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            transport);
    assertFalse(sync.sync(retry).complete());
    store = Session.getInstance(properties).getStore("imap");
    store.connect("localhost", server.getImap().getPort(), "agent", "secret");
    Folder sent = store.getFolder("Sent");
    sent.open(Folder.READ_ONLY);
    assertEquals(0, sent.getMessageCount());
    sent.close(false);
    store.close();
  }

  @Test
  void discoversSpecialUseAndLocalizedSentFolders() throws Exception {
    IMAPFolder special = mock(IMAPFolder.class);
    when(special.getAttributes()).thenReturn(new String[] {"\\Sent"});
    Folder localized = mock(Folder.class);
    when(localized.getName()).thenReturn("Envoyés");
    assertSame(special, SentMailboxSync.sentFolder(new Folder[] {localized, special}));
    assertSame(localized, SentMailboxSync.sentFolder(new Folder[] {localized}));
  }

  @Test
  void recognizesProvidersThatStoreSentMailWithoutAppend() {
    MailConnection connection = new MailConnection();
    connection.setImapHost("imap.gmail.com");
    connection.setSmtpHost("smtp.gmail.com");
    assertTrue(SentMailboxSync.providerStoresSent(connection));
    connection.setImapHost("outlook.office365.com");
    connection.setSmtpHost("smtp.office365.com");
    assertTrue(SentMailboxSync.providerStoresSent(connection));
    connection.setImapHost("imap.example.test");
    connection.setSmtpHost("smtp.example.test");
    assertFalse(SentMailboxSync.providerStoresSent(connection));
  }

  @Test
  void neverUsesAnotherUsersConnectionOrDisconnectedMailbox() throws Exception {
    MailConnection connection = connection();
    MailConnectionRepo repo = mock(MailConnectionRepo.class);
    MailTransportSupport transport = mock(MailTransportSupport.class);
    var sync =
        new SentMailboxSync(
            repo,
            new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            transport);
    var copy = copy(connection);
    assertThrows(ApiException.class, () -> sync.sync(copy));
    verify(repo)
        .findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId());
    connection.setActive(false);
    when(repo.findByIdAndTenantIdAndUserId(
            connection.getId(), connection.getTenantId(), connection.getUserId()))
        .thenReturn(Optional.of(connection));
    assertThrows(ApiException.class, () -> sync.sync(copy));
    verifyNoInteractions(transport);
  }

  private MailConnection connection() {
    var cipher = new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(UUID.randomUUID());
    connection.setUserId(UUID.randomUUID());
    connection.setProvider("IMAP_SMTP");
    connection.setActive(true);
    connection.setStatus("CONNECTED");
    connection.setImapHost("localhost");
    connection.setImapPort(server == null ? 1 : server.getImap().getPort());
    connection.setImapUsername("agent");
    connection.setImapPasswordEncrypted(cipher.encrypt("secret"));
    connection.setImapSecurityMode("STARTTLS");
    connection.setSmtpHost("smtp.example.test");
    return connection;
  }

  private SentCopySyncWorker.Copy copy(MailConnection connection) throws Exception {
    String messageId = "<" + UUID.randomUUID() + "@example.test>";
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    message.setFrom("agent@example.test");
    message.setRecipients(Message.RecipientType.TO, "buyer@example.test");
    message.setSubject("Freight quotation");
    message.setText("Please see the quotation.");
    message.saveChanges();
    message.setHeader("Message-ID", messageId);
    var output = new ByteArrayOutputStream();
    message.writeTo(output);
    return new SentCopySyncWorker.Copy(
        UUID.randomUUID(),
        connection.getTenantId(),
        connection.getUserId(),
        connection.getId(),
        messageId,
        output.toByteArray(),
        0);
  }
}
