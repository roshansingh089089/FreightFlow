package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.entity.CustomerContact;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class MailThreadService {
  private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+@[^<>\\s]+>");
  private final MailThreadRepo threads;
  private final EmailMessageRepo inbound;
  private final OutboundMessageRepo outbound;
  private final ContactRepo contacts;
  private final CustomerRepo customers;
  private final MailConnectionRepo connections;

  MailThreadService(
      MailThreadRepo threads,
      EmailMessageRepo inbound,
      OutboundMessageRepo outbound,
      ContactRepo contacts,
      CustomerRepo customers,
      MailConnectionRepo connections) {
    this.threads = threads;
    this.inbound = inbound;
    this.outbound = outbound;
    this.contacts = contacts;
    this.customers = customers;
    this.connections = connections;
  }

  MailThread create(UUID tenantId, UUID connectionId, String subject, UUID customerId) {
    MailThread thread = new MailThread();
    thread.setTenantId(tenantId);
    thread.setConnectionId(connectionId);
    thread.setNormalizedSubject(
        subject == null ? "" : subject.replaceFirst("(?i)^((re|fw|fwd):\\s*)+", "").trim());
    thread.setCustomerId(customerId);
    return threads.save(thread);
  }

  MailThread resolveInbound(EmailMessage message) {
    List<String> candidates = new ArrayList<>();
    candidates.addAll(ids(message.getReferenceHeaders()));
    candidates.addAll(ids(message.getInReplyTo()));
    Set<UUID> matchedThreads = new HashSet<>();
    for (int i = candidates.size() - 1; i >= 0; i--) {
      String id = candidates.get(i);
      List<EmailMessage> matchingInbound =
          inbound.findByTenantIdAndConnectionIdAndRfcMessageId(
              message.getTenantId(), message.getConnectionId(), id);
      matchingInbound.stream()
          .map(EmailMessage::getThreadId)
          .filter(java.util.Objects::nonNull)
          .forEach(matchedThreads::add);
      outbound
          .findFirstByTenantIdAndConnectionIdAndMessageId(
              message.getTenantId(), message.getConnectionId(), id)
          .map(OutboundMessage::getThreadId)
          .ifPresent(matchedThreads::add);
    }
    if (matchedThreads.size() == 1) {
      UUID id = matchedThreads.iterator().next();
      MailThread thread =
          threads
              .findByIdAndTenantIdAndConnectionId(
                  id, message.getTenantId(), message.getConnectionId())
              .orElse(null);
      if (thread != null) {
        thread.setLastActivityAt(Instant.now());
        return threads.save(thread);
      }
    }
    List<UUID> matches =
        contacts
            .findByTenantIdAndEmailIgnoreCaseAndActiveTrue(
                message.getTenantId(), message.getFromEmail())
            .stream()
            .map(CustomerContact::getCustomerId)
            .distinct()
            .toList();
    MailThread created =
        create(
            message.getTenantId(),
            message.getConnectionId(),
            message.getSubject(),
            matches.size() == 1 ? matches.get(0) : null);
    if (matchedThreads.size() > 1) created.setResolutionStatus("AMBIGUOUS_REFERENCE");
    else if (matches.size() > 1) created.setResolutionStatus("AMBIGUOUS_CUSTOMER");
    return "NORMAL".equals(created.getResolutionStatus()) ? created : threads.save(created);
  }

  MailThread linkCustomer(AppUser user, MailConnection connection, UUID threadId, UUID customerId) {
    MailThread thread = owned(user, connection, threadId);
    if (customers.findByIdAndTenantId(customerId, user.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    thread.setCustomerId(customerId);
    return threads.save(thread);
  }

  void unlinkCustomer(AppUser user, MailConnection connection, UUID threadId) {
    MailThread thread = owned(user, connection, threadId);
    thread.setCustomerId(null);
    threads.save(thread);
  }

  Page<MailThread> customerHistory(AppUser user, UUID customerId, int page, int size) {
    if (customers.findByIdAndTenantId(customerId, user.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    var connection =
        connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId());
    PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
    if (connection.isEmpty()) return Page.empty(pageable);
    return threads.findByTenantIdAndConnectionIdAndCustomerIdOrderByLastActivityAtDesc(
        user.getTenantId(), connection.get().getId(), customerId, pageable);
  }

  MailThread owned(AppUser user, MailConnection connection, UUID id) {
    return threads
        .findByIdAndTenantIdAndConnectionId(id, user.getTenantId(), connection.getId())
        .orElseThrow(
            () -> new ApiException(404, "MAIL_THREAD_NOT_FOUND", "Conversation not found"));
  }

  Page<MailThread> list(AppUser user, MailConnection connection, int page, int size) {
    return threads.findByTenantIdAndConnectionIdOrderByLastActivityAtDesc(
        user.getTenantId(),
        connection.getId(),
        PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
  }

  List<Object> messages(AppUser user, MailConnection connection, UUID threadId) {
    owned(user, connection, threadId);
    List<Object> result = new ArrayList<>();
    result.addAll(
        inbound
            .findByTenantIdAndConnectionIdAndThreadIdOrderByReceivedAtAsc(
                user.getTenantId(), connection.getId(), threadId)
            .stream()
            .map(
                m ->
                    new ThreadMessage(
                        m.getId(),
                        "INBOUND",
                        m.getFromEmail(),
                        m.getToEmail(),
                        m.getSubject(),
                        m.getBodyText(),
                        m.getReceivedAt(),
                        m.getRfcMessageId(),
                        null))
            .toList());
    result.addAll(
        outbound
            .findByTenantIdAndConnectionIdAndThreadIdOrderByCreatedAtAsc(
                user.getTenantId(), connection.getId(), threadId)
            .stream()
            .filter(m -> !"DRAFT".equals(m.getStatus()))
            .map(
                m ->
                    new ThreadMessage(
                        m.getId(),
                        "OUTBOUND",
                        m.getFromAddress(),
                        "",
                        m.getSubject(),
                        m.getBodyText(),
                        m.getSentAt() == null ? m.getCreatedAt() : m.getSentAt(),
                        m.getMessageId(),
                        m.getStatus()))
            .toList());
    result.sort((a, b) -> ((ThreadMessage) a).at().compareTo(((ThreadMessage) b).at()));
    return result;
  }

  static List<String> ids(String headers) {
    List<String> result = new ArrayList<>();
    if (headers == null) return result;
    Matcher matcher = MESSAGE_ID.matcher(headers);
    while (matcher.find()) result.add(matcher.group());
    return result;
  }

  record ThreadMessage(
      UUID id,
      String direction,
      String fromAddress,
      String toAddress,
      String subject,
      String bodyText,
      Instant at,
      String messageId,
      String status) {}
}
