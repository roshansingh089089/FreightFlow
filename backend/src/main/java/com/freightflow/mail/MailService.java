package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class MailService {
  record MailCounts(long inbox, long unread) {}

  private final MailConnectionRepo connections;
  private final EmailMessageRepo messages;
  private final Map<String, MailInboxProvider> providers = new HashMap<>();
  private final MailSynchronizationJob syncJob;

  MailService(
      MailConnectionRepo connections,
      EmailMessageRepo messages,
      List<MailInboxProvider> providers,
      MailSynchronizationJob syncJob) {
    this.connections = connections;
    this.messages = messages;
    this.syncJob = syncJob;
    for (MailInboxProvider p : providers) this.providers.put(p.providerType(), p);
  }

  MailConnection active(AppUser user) {
    return connections
        .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
        .orElseThrow(
            () ->
                new ApiException(
                    409, "MAIL_NOT_CONNECTED", "Connect a mailbox in Settings first."));
  }

  MailSyncResult sync(AppUser user) {
    MailConnection c = active(user);
    if ("IMAP_SMTP".equals(c.getProvider())) return syncJob.sync(c.getId());
    MailInboxProvider p = providers.get(c.getProvider());
    if (p == null)
      throw new ApiException(
          409, "MAIL_PROVIDER_UNSUPPORTED", "This mailbox provider is not supported.");
    MailSyncResult result = p.sync(c, user);
    c.setLastSyncedAt(java.time.Instant.now());
    connections.save(c);
    return result;
  }

  List<GmailIntegration.MessageList> list(AppUser user) {
    MailConnection c = active(user);
    List<EmailMessage> rows =
        "IMAP_SMTP".equals(c.getProvider())
            ? messages.findCurrentImapInbox(
                user.getTenantId(), c.getId(), c.getImapInboxUidValidity(), PageRequest.of(0, 50))
            : messages.findByTenantIdAndConnectionIdOrderByReceivedAtDesc(
                user.getTenantId(), c.getId(), PageRequest.of(0, 50));
    return rows.stream()
        .map(
            m ->
                new GmailIntegration.MessageList(
                    m.getId(),
                    m.getFromName(),
                    m.getFromEmail(),
                    m.getSubject(),
                    m.getReceivedAt(),
                    m.isRead()))
        .toList();
  }

  MailCounts counts(AppUser user) {
    MailConnection c = active(user);
    UUID tenantId = user.getTenantId();
    if ("IMAP_SMTP".equals(c.getProvider())) {
      Long validity = c.getImapInboxUidValidity();
      if (validity == null) return new MailCounts(0, 0);
      return new MailCounts(
          messages.countCurrentImapInbox(tenantId, c.getId(), validity),
          messages.countUnreadCurrentImapInbox(tenantId, c.getId(), validity));
    }
    return new MailCounts(
        messages.countByTenantIdAndConnectionId(tenantId, c.getId()),
        messages.countByTenantIdAndConnectionIdAndReadFalse(tenantId, c.getId()));
  }

  GmailIntegration.MessageDetail detail(AppUser user, UUID id) {
    MailConnection c = active(user);
    EmailMessage m =
        messages
            .findByIdAndTenantIdAndConnectionId(id, user.getTenantId(), c.getId())
            .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Email not found."));
    return new GmailIntegration.MessageDetail(
        m.getId(),
        m.getFromName(),
        m.getFromEmail(),
        m.getToEmail(),
        m.getSubject(),
        m.getBodyText(),
        m.getReceivedAt(),
        m.getProviderThreadId(),
        m.getThreadId());
  }
}
