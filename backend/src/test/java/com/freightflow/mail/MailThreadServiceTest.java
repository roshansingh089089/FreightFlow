package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.entity.MailThread;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.mail.repository.MailThreadRepo;
import com.freightflow.mail.repository.OutboundMessageRepo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MailThreadServiceTest {
  private final MailThreadRepo threads = mock(MailThreadRepo.class);
  private final EmailMessageRepo inbound = mock(EmailMessageRepo.class);
  private final OutboundMessageRepo outbound = mock(OutboundMessageRepo.class);
  private final ContactRepo contacts = mock(ContactRepo.class);
  private final CustomerRepo customers = mock(CustomerRepo.class);
  private final MailThreadService service =
      new MailThreadService(
          threads, inbound, outbound, contacts, customers, mock(MailConnectionRepo.class));

  @Test
  void inboundReplyUsesExistingThreadFromRfcReference() {
    UUID tenant = UUID.randomUUID(), connection = UUID.randomUUID(), threadId = UUID.randomUUID();
    EmailMessage reply = incoming(tenant, connection);
    reply.setInReplyTo("<sent-1@example.com>");
    OutboundMessage sent = new OutboundMessage();
    sent.setThreadId(threadId);
    MailThread thread = new MailThread();
    thread.setId(threadId);
    when(outbound.findFirstByTenantIdAndConnectionIdAndMessageId(
            tenant, connection, "<sent-1@example.com>"))
        .thenReturn(Optional.of(sent));
    when(threads.findByIdAndTenantIdAndConnectionId(threadId, tenant, connection))
        .thenReturn(Optional.of(thread));
    when(threads.save(any(MailThread.class))).thenAnswer(call -> call.getArgument(0));
    assertEquals(threadId, service.resolveInbound(reply).getId());
    verify(contacts, never()).findByTenantIdAndEmailIgnoreCaseAndActiveTrue(any(), any());
  }

  @Test
  void matchingSubjectAloneDoesNotMergeThreads() {
    EmailMessage first = incoming(UUID.randomUUID(), UUID.randomUUID());
    first.setSubject("Re: Ocean freight");
    when(contacts.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(any(), any()))
        .thenReturn(List.of());
    when(threads.save(any(MailThread.class))).thenAnswer(call -> call.getArgument(0));
    MailThread created = service.resolveInbound(first);
    assertEquals("Ocean freight", created.getNormalizedSubject());
    verify(threads, never()).findByIdAndTenantIdAndConnectionId(any(), any(), any());
  }

  @Test
  void conflictingReferenceHeadersRequireReviewInsteadOfMerging() {
    UUID tenant = UUID.randomUUID(), connection = UUID.randomUUID();
    EmailMessage reply = incoming(tenant, connection);
    reply.setReferenceHeaders("<first@example.com> <second@example.com>");
    EmailMessage first = incoming(tenant, connection);
    first.setThreadId(UUID.randomUUID());
    EmailMessage second = incoming(tenant, connection);
    second.setThreadId(UUID.randomUUID());
    when(inbound.findByTenantIdAndConnectionIdAndRfcMessageId(
            tenant, connection, "<first@example.com>"))
        .thenReturn(List.of(first));
    when(inbound.findByTenantIdAndConnectionIdAndRfcMessageId(
            tenant, connection, "<second@example.com>"))
        .thenReturn(List.of(second));
    when(contacts.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(any(), any()))
        .thenReturn(List.of());
    when(threads.save(any(MailThread.class))).thenAnswer(call -> call.getArgument(0));
    MailThread resolved = service.resolveInbound(reply);
    assertEquals("AMBIGUOUS_REFERENCE", resolved.getResolutionStatus());
    assertNotEquals(first.getThreadId(), resolved.getId());
    assertNotEquals(second.getThreadId(), resolved.getId());
  }

  @Test
  void crossTenantCustomerCannotBeLinkedToThread() {
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    UUID threadId = UUID.randomUUID(), foreignCustomer = UUID.randomUUID();
    MailThread thread = new MailThread();
    thread.setId(threadId);
    when(threads.findByIdAndTenantIdAndConnectionId(
            threadId, user.getTenantId(), connection.getId()))
        .thenReturn(Optional.of(thread));
    assertEquals(
        "NOT_FOUND",
        assertThrows(
                ApiException.class,
                () -> service.linkCustomer(user, connection, threadId, foreignCustomer))
            .code);
    verify(customers).findByIdAndTenantId(foreignCustomer, user.getTenantId());
    verify(threads, never()).save(any());
  }

  private static EmailMessage incoming(UUID tenant, UUID connection) {
    EmailMessage message = new EmailMessage();
    message.setTenantId(tenant);
    message.setConnectionId(connection);
    message.setFromEmail("customer@example.com");
    return message;
  }
}
