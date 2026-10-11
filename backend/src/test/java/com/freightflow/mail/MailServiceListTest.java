package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class MailServiceListTest {
  @Test
  void switchingActiveMailboxesUsesNewConnectionAndUidEpoch() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    AppUser user = new AppUser();
    user.setId(userId);
    user.setTenantId(tenantId);
    MailConnection first = new MailConnection();
    first.setId(UUID.randomUUID());
    first.setProvider("IMAP_SMTP");
    first.setImapInboxUidValidity(10L);
    MailConnection second = new MailConnection();
    second.setId(UUID.randomUUID());
    second.setProvider("IMAP_SMTP");
    second.setImapInboxUidValidity(20L);
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    when(connections.findByTenantIdAndUserIdAndActiveTrue(tenantId, userId))
        .thenReturn(Optional.of(first), Optional.of(second));
    when(messages.countCurrentImapInbox(tenantId, first.getId(), 10L)).thenReturn(4L);
    when(messages.countUnreadCurrentImapInbox(tenantId, first.getId(), 10L)).thenReturn(2L);
    when(messages.countCurrentImapInbox(tenantId, second.getId(), 20L)).thenReturn(1L);
    MailService service =
        new MailService(connections, messages, List.of(), mock(MailSynchronizationJob.class));

    assertEquals(new MailService.MailCounts(4, 2), service.counts(user));
    assertEquals(new MailService.MailCounts(1, 0), service.counts(user));
  }

  @Test
  void listsAndCountsOnlyCurrentImapInbox() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID connectionId = UUID.randomUUID();
    AppUser user = new AppUser();
    user.setId(userId);
    user.setTenantId(tenantId);
    MailConnection connection = new MailConnection();
    connection.setId(connectionId);
    connection.setProvider("IMAP_SMTP");
    connection.setImapInboxUidValidity(123L);
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    EmailMessageRepo messages = mock(EmailMessageRepo.class);
    when(connections.findByTenantIdAndUserIdAndActiveTrue(tenantId, userId))
        .thenReturn(Optional.of(connection));

    EmailMessage current = message("uid:123:1", "<freight-enquiry@example.test>", 1L);
    when(messages.findCurrentImapInbox(
            eq(tenantId), eq(connectionId), eq(123L), any(Pageable.class)))
        .thenReturn(List.of(current));
    when(messages.countCurrentImapInbox(tenantId, connectionId, 123L)).thenReturn(1L);
    when(messages.countUnreadCurrentImapInbox(tenantId, connectionId, 123L)).thenReturn(1L);
    MailService service =
        new MailService(connections, messages, List.of(), mock(MailSynchronizationJob.class));

    var result = service.list(user);

    assertEquals(1, result.size());
    assertEquals(current.getId(), result.get(0).id());
    assertEquals(new MailService.MailCounts(1, 1), service.counts(user));
  }

  private static EmailMessage message(String providerId, String rfcId, Long uid) {
    EmailMessage row = new EmailMessage();
    row.setProviderMessageId(providerId);
    row.setRfcMessageId(rfcId);
    row.setImapUid(uid);
    row.setReceivedAt(Instant.now());
    row.setSubject("Freight enquiry");
    return row;
  }
}
