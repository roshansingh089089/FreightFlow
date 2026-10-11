package com.freightflow.customer;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.entity.CustomerMailLink;
import com.freightflow.customer.repository.CustomerMailLinkRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.entity.OutboundRecipient;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.OutboundMessageRepo;
import com.freightflow.mail.repository.OutboundRecipientRepo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

record ConversationMessage(
    UUID id,
    String direction,
    String subject,
    String fromAddress,
    String toAddress,
    String status,
    Instant at) {}

record CustomerConversation(String key, List<ConversationMessage> messages) {}

@Service
class CustomerConversationService {
  private static final Pattern ID = Pattern.compile("<[^<>\\s]+>");
  private final CustomerRepo customers;
  private final CustomerMailLinkRepo links;
  private final EmailMessageRepo inbound;
  private final OutboundMessageRepo outbound;
  private final OutboundRecipientRepo recipients;

  CustomerConversationService(
      CustomerRepo customers,
      CustomerMailLinkRepo links,
      EmailMessageRepo inbound,
      OutboundMessageRepo outbound,
      OutboundRecipientRepo recipients) {
    this.customers = customers;
    this.links = links;
    this.inbound = inbound;
    this.outbound = outbound;
    this.recipients = recipients;
  }

  List<CustomerConversation> customer(UUID customerId, int limit) {
    AppUser user = SecurityConfig.current();
    UUID tenant = user.getTenantId();
    if (customers.findByIdAndTenantId(customerId, tenant).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    int bounded = Math.min(100, Math.max(1, limit));
    Map<String, List<ConversationMessage>> groups = new LinkedHashMap<>();
    for (CustomerMailLink link :
        links.findByTenantIdAndCustomerIdOrderByLinkedAtDesc(
            tenant, customerId, PageRequest.of(0, bounded))) {
      EmailMessage message = inbound.findByIdAndTenantId(link.getMessageId(), tenant).orElse(null);
      if (message == null) continue;
      String key =
          key(
              message.getReferenceHeaders(),
              message.getInReplyTo(),
              message.getRfcMessageId(),
              message.getProviderThreadId(),
              message.getId());
      groups
          .computeIfAbsent(key, unused -> new ArrayList<>())
          .add(
              new ConversationMessage(
                  message.getId(),
                  "INBOUND",
                  message.getSubject(),
                  message.getFromEmail(),
                  message.getToEmail(),
                  "RECEIVED",
                  message.getReceivedAt()));
    }
    for (OutboundMessage message :
        outbound.customerHistory(
            tenant,
            customerId,
            user.getId(),
            PageRequest.of(0, bounded, Sort.by(Sort.Direction.DESC, "createdAt")))) {
      String key =
          key(
              message.getReferenceHeaders(),
              message.getInReplyTo(),
              message.getMessageId(),
              null,
              message.getId());
      String to =
          recipients.findByTenantIdAndMessageId(tenant, message.getId()).stream()
              .filter(r -> "TO".equals(r.getRecipientType()))
              .map(OutboundRecipient::getEmail)
              .reduce((a, b) -> a + ", " + b)
              .orElse("");
      groups
          .computeIfAbsent(key, unused -> new ArrayList<>())
          .add(
              new ConversationMessage(
                  message.getId(),
                  "OUTBOUND",
                  message.getSubject(),
                  message.getFromAddress(),
                  to,
                  message.getStatus(),
                  message.getSentAt() == null ? message.getCreatedAt() : message.getSentAt()));
    }
    return groups.entrySet().stream()
        .map(
            entry -> {
              entry
                  .getValue()
                  .sort(
                      Comparator.comparing(
                          ConversationMessage::at,
                          Comparator.nullsLast(Comparator.naturalOrder())));
              return new CustomerConversation(entry.getKey(), entry.getValue());
            })
        .sorted(
            Comparator.comparing(
                    (CustomerConversation group) -> group.messages().getLast().at(),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed())
        .toList();
  }

  private static String key(
      String references,
      String inReplyTo,
      String messageId,
      String providerThreadId,
      UUID fallback) {
    for (String value :
        List.of(
            Objects.toString(references, ""),
            Objects.toString(inReplyTo, ""),
            Objects.toString(messageId, ""))) {
      Matcher match = ID.matcher(value);
      if (match.find()) return match.group().toLowerCase(Locale.ROOT);
    }
    return providerThreadId != null && !providerThreadId.isBlank()
        ? "provider:" + providerThreadId
        : "local:" + fallback;
  }
}
