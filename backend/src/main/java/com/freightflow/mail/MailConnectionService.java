package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MailConnectionService {
  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;

  MailConnectionService(
      MailConnectionRepo connections, MailCredentialCipher cipher, MailTransportSupport transport) {
    this.connections = connections;
    this.cipher = cipher;
    this.transport = transport;
  }

  private static MailConnectionController.View view(MailConnection c) {
    return new MailConnectionController.View(
        c.getId(),
        c.getProvider(),
        c.getEmailAddress(),
        c.getStatus(),
        c.isActive(),
        c.getLastSyncedAt(),
        c.getImapHost(),
        c.getImapPort(),
        c.getImapSecurityMode(),
        c.getSmtpHost(),
        c.getSmtpPort(),
        c.getSmtpSecurityMode());
  }

  MailConnectionController.View current(AppUser user) {
    MailConnection latest =
        connections
            .findFirstByTenantIdAndUserIdOrderByUpdatedAtDesc(user.getTenantId(), user.getId())
            .orElse(null);
    if (latest != null
        && ("PENDING_TEST".equals(latest.getStatus()) || "TEST_FAILED".equals(latest.getStatus())))
      return view(latest);
    return connections
        .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
        .map(MailConnectionService::view)
        .orElse(latest == null ? null : view(latest));
  }

  MailConnectionController.TestResult testCandidate(MailConnectionController.SaveRequest r) {
    validateRequest(r);
    return testCredentials(
        r.imapHost(),
        r.imapPort(),
        r.imapUsername(),
        r.imapPassword(),
        r.imapSecurityMode(),
        r.smtpHost(),
        r.smtpPort(),
        r.smtpUsername(),
        r.smtpPassword(),
        r.smtpSecurityMode());
  }

  private void validateRequest(MailConnectionController.SaveRequest r) {
    if (!"IMAP_SMTP".equals(r.providerType()))
      throw new ApiException(400, "MAIL_PROVIDER_INVALID", "Choose an IMAP/SMTP mailbox.");
    transport.validate(r.imapHost(), r.imapPort(), r.imapSecurityMode());
    transport.validate(r.smtpHost(), r.smtpPort(), r.smtpSecurityMode());
  }

  private MailConnectionController.TestResult testCredentials(
      String imapHost,
      int imapPort,
      String imapUser,
      String imapPassword,
      String imapMode,
      String smtpHost,
      int smtpPort,
      String smtpUser,
      String smtpPassword,
      String smtpMode) {
    boolean imap = false, smtp = false;
    Store store = null;
    Transport sender = null;
    try {
      Session s = transport.session("imap", imapHost, imapPort, imapMode);
      store = s.getStore("imap");
      store.connect(imapHost, imapPort, imapUser, imapPassword);
      Folder inbox = store.getFolder("INBOX");
      imap = inbox.exists();
    } catch (Exception ignored) {
    } finally {
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {
      }
    }
    try {
      Session s = transport.session("smtp", smtpHost, smtpPort, smtpMode);
      sender = s.getTransport("smtp");
      sender.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
      smtp = true;
    } catch (Exception ignored) {
    } finally {
      try {
        if (sender != null && sender.isConnected()) sender.close();
      } catch (Exception ignored) {
      }
    }
    return new MailConnectionController.TestResult(
        imap,
        smtp,
        imap && smtp
            ? "IMAP OK · SMTP OK"
            : "Connection test failed. Check the server settings and mailbox password.");
  }

  @Transactional
  MailConnectionController.View save(AppUser user, MailConnectionController.SaveRequest r) {
    MailConnectionController.TestResult result = testCandidate(r);
    if (!result.imapOk() || !result.smtpOk())
      throw new ApiException(422, "MAIL_CONNECTION_TEST_FAILED", result.message());
    MailConnection c =
        connections
            .findByTenantIdAndUserIdAndProviderAndEmailAddressIgnoreCase(
                user.getTenantId(), user.getId(), "IMAP_SMTP", r.emailAddress().trim())
            .orElseGet(MailConnection::new);
    c.setTenantId(user.getTenantId());
    c.setUserId(user.getId());
    c.setProvider("IMAP_SMTP");
    c.setEmailAddress(r.emailAddress().trim());
    c.setImapHost(r.imapHost().trim());
    c.setImapPort(r.imapPort());
    c.setImapUsername(r.imapUsername().trim());
    c.setImapPasswordEncrypted(cipher.encrypt(r.imapPassword()));
    c.setImapSecurityMode(r.imapSecurityMode());
    c.setSmtpHost(r.smtpHost().trim());
    c.setSmtpPort(r.smtpPort());
    c.setSmtpUsername(r.smtpUsername().trim());
    c.setSmtpPasswordEncrypted(cipher.encrypt(r.smtpPassword()));
    c.setSmtpSecurityMode(r.smtpSecurityMode());
    c.setSmtpFrom(r.smtpFrom().trim());
    connections
        .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
        .filter(previous -> !previous.getId().equals(c.getId()))
        .ifPresent(
            previous -> {
              previous.setActive(false);
              connections.saveAndFlush(previous);
            });
    c.setActive(true);
    c.setStatus("CONNECTED");
    return view(connections.save(c));
  }

  @Transactional
  MailConnectionController.TestResult test(AppUser user, UUID id) {
    MailConnection c = owned(user, id);
    if (!"IMAP_SMTP".equals(c.getProvider()) || "DISCONNECTED".equals(c.getStatus()))
      throw new ApiException(409, "MAIL_CONNECTION_INVALID", "This connection cannot be tested.");
    transport.validate(c.getImapHost(), c.getImapPort(), c.getImapSecurityMode());
    transport.validate(c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode());
    boolean imap = false, smtp = false;
    Store store = null;
    Transport sender = null;
    try {
      Session s =
          transport.session("imap", c.getImapHost(), c.getImapPort(), c.getImapSecurityMode());
      store = s.getStore("imap");
      store.connect(
          c.getImapHost(),
          c.getImapPort(),
          c.getImapUsername(),
          cipher.decrypt(c.getImapPasswordEncrypted()));
      Folder inbox = store.getFolder("INBOX");
      imap = inbox.exists();
    } catch (Exception ignored) {
    } finally {
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {
      }
    }
    try {
      Session s =
          transport.session("smtp", c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode());
      sender = s.getTransport("smtp");
      sender.connect(
          c.getSmtpHost(),
          c.getSmtpPort(),
          c.getSmtpUsername(),
          cipher.decrypt(c.getSmtpPasswordEncrypted()));
      smtp = true;
    } catch (Exception ignored) {
    } finally {
      try {
        if (sender != null && sender.isConnected()) sender.close();
      } catch (Exception ignored) {
      }
    }
    if (imap && smtp) {
      connections
          .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
          .filter(previous -> !previous.getId().equals(c.getId()))
          .ifPresent(
              previous -> {
                previous.setActive(false);
                connections.saveAndFlush(previous);
              });
      c.setActive(true);
      c.setStatus("CONNECTED");
      connections.save(c);
    } else {
      c.setActive(false);
      c.setStatus("TEST_FAILED");
      connections.save(c);
    }
    return new MailConnectionController.TestResult(
        imap,
        smtp,
        imap && smtp
            ? "Mailbox connected."
            : "Connection test failed. Check the server settings and app-specific password.");
  }

  @Transactional
  void disconnect(AppUser user, UUID id) {
    MailConnection c = owned(user, id);
    c.setActive(false);
    c.setStatus("DISCONNECTED");
    c.setImapPasswordEncrypted(null);
    c.setSmtpPasswordEncrypted(null);
    if ("GMAIL_API".equals(c.getProvider())) {
      c.setAccessToken(null);
      c.setRefreshToken(null);
      c.setTokenExpiry(null);
    }
    connections.save(c);
  }

  private MailConnection owned(AppUser user, UUID id) {
    return connections
        .findByIdAndTenantIdAndUserId(id, user.getTenantId(), user.getId())
        .orElseThrow(
            () ->
                new ApiException(
                    404, "MAIL_CONNECTION_NOT_FOUND", "Mailbox connection not found."));
  }
}
