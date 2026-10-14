package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.entity.CustomerExtraCc;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerExtraCcRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.entity.OutboundAttachment;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.entity.OutboundRecipient;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.mail.repository.OutboundAttachmentRepo;
import com.freightflow.mail.repository.OutboundMessageRepo;
import com.freightflow.mail.repository.OutboundRecipientRepo;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.repository.QuoteRepo;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

record MailAddress(@Email @NotBlank String email, String displayName) {}

record DraftRequest(
    UUID customerId,
    UUID quotationId,
    List<@Valid MailAddress> to,
    List<@Valid MailAddress> cc,
    List<@Valid MailAddress> bcc,
    @Size(max = 998) String subject,
    String bodyText,
    List<UUID> attachmentIds,
    String inReplyTo,
    String references) {}

record SendDraftRequest(@NotBlank String idempotencyKey) {}

record ReconcileMailRequest(@NotBlank String resolution, @NotBlank String note) {}

record DraftResponse(
    UUID id,
    String status,
    UUID customerId,
    UUID quotationId,
    List<MailAddress> to,
    List<MailAddress> cc,
    List<MailAddress> bcc,
    String subject,
    String bodyText,
    List<UUID> attachmentIds,
    Instant createdAt,
    Instant updatedAt,
    String errorCode,
    String composeMode,
    UUID connectionId,
    UUID threadId,
    UUID replyToMessageId,
    UUID replyToOutboundMessageId,
    Instant reconciledAt,
    String reconciliationNote,
    String threadingWarning,
    String fromAddress,
    Instant sentAt) {}

@Service
class OutboundMailService {
  private final OutboundMessageRepo messages;
  private final OutboundRecipientRepo recipients;
  private final OutboundAttachmentRepo attachments;
  private final CustomerRepo customers;
  private final ContactRepo contacts;
  private final CustomerExtraCcRepo extraCc;
  private final QuoteRepo quotes;
  private final MailConnectionRepo connections;
  private final EmailMessageRepo inbound;
  private final MailThreadService threads;

  OutboundMailService(
      OutboundMessageRepo messages,
      OutboundRecipientRepo recipients,
      OutboundAttachmentRepo attachments,
      CustomerRepo customers,
      ContactRepo contacts,
      CustomerExtraCcRepo extraCc,
      QuoteRepo quotes,
      MailConnectionRepo connections,
      EmailMessageRepo inbound,
      MailThreadService threads) {
    this.messages = messages;
    this.recipients = recipients;
    this.attachments = attachments;
    this.customers = customers;
    this.contacts = contacts;
    this.extraCc = extraCc;
    this.quotes = quotes;
    this.connections = connections;
    this.inbound = inbound;
    this.threads = threads;
  }

  private AppUser user() {
    return SecurityConfig.current();
  }

  private OutboundMessage owned(UUID id) {
    AppUser u = user();
    return messages
        .findByIdAndTenantIdAndCreatedBy(id, u.getTenantId(), u.getId())
        .orElseThrow(() -> new ApiException(404, "MAIL_DRAFT_NOT_FOUND", "Draft not found"));
  }

  private void validateLinks(DraftRequest r) {
    AppUser u = user();
    if (r.customerId() != null
        && customers.findByIdAndTenantId(r.customerId(), u.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    if (r.quotationId() != null) {
      Quotation q =
          quotes
              .findByIdAndTenantId(r.quotationId(), u.getTenantId())
              .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
      if (r.customerId() != null && !q.getCustomerId().equals(r.customerId()))
        throw new ApiException(
            400, "MAIL_CUSTOMER_MISMATCH", "Quotation does not belong to customer");
    }
  }

  @Transactional
  DraftResponse create(DraftRequest r) {
    validateLinks(r);
    AppUser u = user();
    OutboundMessage m = new OutboundMessage();
    m.setTenantId(u.getTenantId());
    m.setCreatedBy(u.getId());
    m.setComposeMode("NEW");
    connections
        .findByTenantIdAndUserIdAndActiveTrue(u.getTenantId(), u.getId())
        .filter(c -> "IMAP_SMTP".equals(c.getProvider()) && "CONNECTED".equals(c.getStatus()))
        .ifPresent(c -> m.setConnectionId(c.getId()));
    apply(m, r);
    messages.save(m);
    setRecipients(m, r);
    setAttachments(m, r);
    return view(m);
  }

  @Transactional
  DraftResponse update(UUID id, DraftRequest r) {
    OutboundMessage m = owned(id);
    if (!"DRAFT".equals(m.getStatus()))
      throw new ApiException(409, "MAIL_DRAFT_LOCKED", "Only drafts can be edited");
    validateLinks(r);
    apply(m, r);
    if (!"NEW".equals(m.getComposeMode())) {
      if (m.getReplyToOutboundMessageId() != null) applySentReplyHeaders(m, sentReplySource(m));
      else applyReplyHeaders(m, replySource(m));
    }
    messages.save(m);
    recipients.deleteByTenantIdAndMessageId(m.getTenantId(), m.getId());
    recipients.flush();
    setRecipients(m, r);
    setAttachments(m, r);
    return view(m);
  }

  @Transactional
  void discard(UUID id) {
    OutboundMessage m = owned(id);
    if (!"DRAFT".equals(m.getStatus()))
      throw new ApiException(409, "MAIL_DRAFT_LOCKED", "Only drafts can be discarded");
    messages.delete(m);
  }

  private void apply(OutboundMessage m, DraftRequest r) {
    m.setCustomerId(r.customerId());
    m.setQuotationId(r.quotationId());
    m.setSubject(r.subject() == null ? "" : r.subject().trim());
    m.setBodyText(r.bodyText() == null ? "" : r.bodyText());
    if ("NEW".equals(m.getComposeMode())) {
      m.setInReplyTo(null);
      m.setReferenceHeaders(null);
    }
  }

  @Transactional
  DraftResponse createReply(UUID originalId, boolean all) {
    AppUser u = user();
    MailConnection c =
        connections
            .findByTenantIdAndUserIdAndActiveTrue(u.getTenantId(), u.getId())
            .orElseThrow(
                () -> new ApiException(409, "MAIL_NOT_CONNECTED", "Connect a mailbox first"));
    EmailMessage original =
        inbound
            .findByIdAndTenantIdAndConnectionId(originalId, u.getTenantId(), c.getId())
            .orElse(null);
    if (original == null) return createSentReply(originalId, all, u, c);
    OutboundMessage m = new OutboundMessage();
    m.setTenantId(u.getTenantId());
    m.setCreatedBy(u.getId());
    m.setConnectionId(c.getId());
    m.setComposeMode(all ? "REPLY_ALL" : "REPLY");
    m.setReplyToMessageId(original.getId());
    if (original.getThreadId() == null) {
      original.setThreadId(threads.resolveInbound(original).getId());
      inbound.save(original);
    }
    m.setThreadId(original.getThreadId());
    m.setCustomerId(threads.owned(u, c, original.getThreadId()).getCustomerId());
    m.setSubject(
        original.getSubject() == null
            ? "Re: "
            : original.getSubject().matches("(?i)^re:.*")
                ? original.getSubject()
                : "Re: " + original.getSubject());
    applyReplyHeaders(m, original);
    messages.save(m);
    Set<String> seen = new HashSet<>();
    Set<String> ownAddresses = new HashSet<>();
    if (c.getSmtpFrom() != null) ownAddresses.add(c.getSmtpFrom().toLowerCase(Locale.ROOT));
    ownAddresses.add(c.getEmailAddress().toLowerCase(Locale.ROOT));
    addReplyAddresses(
        m,
        "TO",
        original.getReplyToEmail() == null || original.getReplyToEmail().isBlank()
            ? original.getFromEmail()
            : original.getReplyToEmail(),
        seen,
        ownAddresses);
    if (all) {
      addReplyAddresses(m, "TO", original.getToEmail(), seen, ownAddresses);
      addReplyAddresses(m, "CC", original.getCcEmail(), seen, ownAddresses);
    }
    return view(m);
  }

  private DraftResponse createSentReply(UUID originalId, boolean all, AppUser u, MailConnection c) {
    OutboundMessage original =
        messages
            .findByIdAndTenantIdAndConnectionIdAndStatus(
                originalId, u.getTenantId(), c.getId(), "SENT")
            .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Email not found"));
    if (original.getThreadId() == null) {
      original.setThreadId(
          threads
              .create(u.getTenantId(), c.getId(), original.getSubject(), original.getCustomerId())
              .getId());
      messages.save(original);
    }
    OutboundMessage m = new OutboundMessage();
    m.setTenantId(u.getTenantId());
    m.setCreatedBy(u.getId());
    m.setConnectionId(c.getId());
    m.setComposeMode(all ? "REPLY_ALL" : "REPLY");
    m.setReplyToOutboundMessageId(original.getId());
    m.setThreadId(original.getThreadId());
    m.setCustomerId(original.getCustomerId());
    m.setSubject(replySubject(original.getSubject()));
    applySentReplyHeaders(m, original);
    messages.save(m);
    Set<String> seen = new HashSet<>();
    Set<String> ownAddresses = new HashSet<>();
    if (c.getSmtpFrom() != null) ownAddresses.add(c.getSmtpFrom().toLowerCase(Locale.ROOT));
    ownAddresses.add(c.getEmailAddress().toLowerCase(Locale.ROOT));
    for (OutboundRecipient recipient :
        recipients.findByTenantIdAndMessageId(u.getTenantId(), original.getId())) {
      if ("TO".equals(recipient.getRecipientType())
          || (all && "CC".equals(recipient.getRecipientType())))
        addReplyAddresses(
            m, recipient.getRecipientType(), recipient.getEmail(), seen, ownAddresses);
    }
    return view(m);
  }

  private static String replySubject(String subject) {
    if (subject == null) return "Re: ";
    return subject.matches("(?i)^re:.*") ? subject : "Re: " + subject;
  }

  private EmailMessage replySource(OutboundMessage m) {
    return inbound
        .findByIdAndTenantIdAndConnectionId(
            m.getReplyToMessageId(), m.getTenantId(), m.getConnectionId())
        .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Original email not found"));
  }

  private OutboundMessage sentReplySource(OutboundMessage m) {
    return messages
        .findByIdAndTenantIdAndConnectionIdAndStatus(
            m.getReplyToOutboundMessageId(), m.getTenantId(), m.getConnectionId(), "SENT")
        .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Original email not found"));
  }

  private void applySentReplyHeaders(OutboundMessage m, OutboundMessage original) {
    String originalId = original.getMessageId();
    List<String> parsed = MailThreadService.ids(originalId);
    String validId =
        originalId != null && parsed.size() == 1 && parsed.get(0).equals(originalId.trim())
            ? parsed.get(0)
            : null;
    m.setInReplyTo(validId);
    List<String> chain = new ArrayList<>(MailThreadService.ids(original.getReferenceHeaders()));
    if (validId != null) chain.add(validId);
    m.setReferenceHeaders(
        chain.isEmpty() ? null : String.join(" ", chain.stream().distinct().toList()));
  }

  private void applyReplyHeaders(OutboundMessage m, EmailMessage original) {
    String originalId = original.getRfcMessageId();
    List<String> parsed = MailThreadService.ids(originalId);
    String validId =
        originalId != null && parsed.size() == 1 && parsed.get(0).equals(originalId.trim())
            ? parsed.get(0)
            : null;
    m.setInReplyTo(validId);
    List<String> chain = new ArrayList<>(MailThreadService.ids(original.getReferenceHeaders()));
    if (validId != null) chain.add(validId);
    m.setReferenceHeaders(
        chain.isEmpty() ? null : String.join(" ", chain.stream().distinct().toList()));
  }

  private void addReplyAddresses(
      OutboundMessage m, String type, String raw, Set<String> seen, Set<String> ownAddresses) {
    if (raw == null || raw.isBlank()) return;
    try {
      for (InternetAddress address : InternetAddress.parse(raw, true)) {
        String email = address.getAddress().toLowerCase(Locale.ROOT);
        if (ownAddresses.contains(email) || !seen.add(email)) continue;
        OutboundRecipient row = new OutboundRecipient();
        row.setTenantId(m.getTenantId());
        row.setMessageId(m.getId());
        row.setRecipientType(type);
        row.setEmail(email);
        row.setDisplayName(address.getPersonal());
        recipients.save(row);
      }
    } catch (Exception e) {
      throw new ApiException(
          400, "MAIL_RECIPIENT_INVALID", "Original email contains an invalid recipient");
    }
  }

  private void setRecipients(OutboundMessage m, DraftRequest r) {
    Set<String> seen = new HashSet<>();
    for (String type : List.of("TO", "CC", "BCC")) {
      List<MailAddress> input =
          switch (type) {
            case "TO" -> r.to();
            case "CC" -> r.cc();
            default -> r.bcc();
          };
      if (input == null) continue;
      for (MailAddress address : input) {
        String email = address.email().trim().toLowerCase(Locale.ROOT);
        if (!seen.add(email))
          throw new ApiException(
              400,
              "MAIL_RECIPIENT_DUPLICATE",
              "An address can appear only once across To, CC and BCC");
        OutboundRecipient row = new OutboundRecipient();
        row.setTenantId(m.getTenantId());
        row.setMessageId(m.getId());
        row.setRecipientType(type);
        row.setEmail(email);
        row.setDisplayName(address.displayName());
        recipients.save(row);
      }
    }
  }

  private void setAttachments(OutboundMessage m, DraftRequest r) {
    for (OutboundAttachment old :
        attachments.findByTenantIdAndMessageId(m.getTenantId(), m.getId())) {
      old.setMessageId(null);
      attachments.save(old);
    }
    if (r.attachmentIds() == null) return;
    for (UUID id : new HashSet<>(r.attachmentIds())) {
      OutboundAttachment attachment =
          attachments
              .findByIdAndTenantId(id, m.getTenantId())
              .orElseThrow(
                  () -> new ApiException(404, "MAIL_ATTACHMENT_NOT_FOUND", "Attachment not found"));
      if (!attachment.getUploadedBy().equals(m.getCreatedBy())
          || attachment.getMessageId() != null && !attachment.getMessageId().equals(m.getId()))
        throw new ApiException(
            403, "MAIL_ATTACHMENT_FORBIDDEN", "Attachment does not belong to this draft");
      attachment.setMessageId(m.getId());
      attachments.save(attachment);
    }
  }

  void lockQuotationForLegacy(UUID quotationId) {
    AppUser u = user();
    quotes
        .lockForMailSubmission(quotationId, u.getTenantId())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
  }

  DraftResponse existingSubmission(String key, UUID quotationId) {
    AppUser u = user();
    return messages
        .findByTenantIdAndCreatedByAndIdempotencyKey(u.getTenantId(), u.getId(), key)
        .map(
            m -> {
              if (!quotationId.equals(m.getQuotationId()))
                throw new ApiException(
                    409, "MAIL_IDEMPOTENCY_REUSED", "This send key was already used");
              return view(m);
            })
        .orElse(null);
  }

  DraftResponse get(UUID id) {
    return view(owned(id));
  }

  Page<DraftResponse> list(String status, int page, int size) {
    AppUser u = user();
    PageRequest pageable =
        PageRequest.of(
            Math.max(0, page),
            Math.min(100, Math.max(1, size)),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return status == null || status.isBlank()
        ? messages.findByTenantIdAndCreatedBy(u.getTenantId(), u.getId(), pageable).map(this::view)
        : messages
            .findByTenantIdAndCreatedByAndStatus(u.getTenantId(), u.getId(), status, pageable)
            .map(this::view);
  }

  Page<DraftResponse> customerHistory(UUID customerId, int page, int size) {
    AppUser u = user();
    if (customers.findByIdAndTenantId(customerId, u.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    return messages
        .customerHistory(
            u.getTenantId(),
            customerId,
            u.getId(),
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::view);
  }

  Page<DraftResponse> search(
      UUID customer,
      UUID quotation,
      String sender,
      String recipient,
      String subject,
      String status,
      Instant from,
      Instant to,
      int page,
      int size) {
    AppUser u = user();
    if (customer != null && customers.findByIdAndTenantId(customer, u.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Customer not found");
    if (quotation != null && quotes.findByIdAndTenantId(quotation, u.getTenantId()).isEmpty())
      throw new ApiException(404, "NOT_FOUND", "Quotation not found");
    if (from != null && to != null && !from.isBefore(to))
      throw new ApiException(400, "MAIL_DATE_RANGE_INVALID", "End date must follow start date");
    Specification<OutboundMessage> filters =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("tenantId"), u.getTenantId()),
                cb.equal(root.get("createdBy"), u.getId()));
    if (customer != null)
      filters = filters.and((root, query, cb) -> cb.equal(root.get("customerId"), customer));
    if (quotation != null)
      filters = filters.and((root, query, cb) -> cb.equal(root.get("quotationId"), quotation));
    if (!normalize(sender).isEmpty())
      filters =
          filters.and(
              (root, query, cb) ->
                  cb.like(
                      cb.lower(root.get("fromAddress")),
                      "%" + sender.trim().toLowerCase(Locale.ROOT) + "%"));
    if (!normalize(subject).isEmpty())
      filters =
          filters.and(
              (root, query, cb) ->
                  cb.like(
                      cb.lower(root.get("subject")),
                      "%" + subject.trim().toLowerCase(Locale.ROOT) + "%"));
    if (!normalize(status).isEmpty())
      filters = filters.and((root, query, cb) -> cb.equal(root.get("status"), status.trim()));
    if (from != null)
      filters =
          filters.and(
              (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
    if (to != null)
      filters = filters.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), to));
    if (!normalize(recipient).isEmpty()) {
      String pattern = "%" + recipient.trim().toLowerCase(Locale.ROOT) + "%";
      filters =
          filters.and(
              (root, query, cb) -> {
                var sub = query.subquery(UUID.class);
                var r = sub.from(OutboundRecipient.class);
                sub.select(r.get("id"))
                    .where(
                        cb.equal(r.get("messageId"), root.get("id")),
                        cb.equal(r.get("tenantId"), u.getTenantId()),
                        cb.like(cb.lower(r.get("email")), pattern));
                return cb.exists(sub);
              });
    }
    return messages
        .findAll(
            filters,
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::view);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private DraftResponse view(OutboundMessage m) {
    List<OutboundRecipient> rows =
        recipients.findByTenantIdAndMessageId(m.getTenantId(), m.getId());
    return new DraftResponse(
        m.getId(),
        m.getStatus(),
        m.getCustomerId(),
        m.getQuotationId(),
        addresses(rows, "TO"),
        addresses(rows, "CC"),
        addresses(rows, "BCC"),
        m.getSubject(),
        m.getBodyText(),
        attachments.findByTenantIdAndMessageId(m.getTenantId(), m.getId()).stream()
            .map(OutboundAttachment::getId)
            .toList(),
        m.getCreatedAt(),
        m.getUpdatedAt(),
        m.getErrorCode(),
        m.getComposeMode(),
        m.getConnectionId(),
        m.getThreadId(),
        m.getReplyToMessageId(),
        m.getReplyToOutboundMessageId(),
        m.getReconciledAt(),
        m.getReconciliationNote(),
        !"NEW".equals(m.getComposeMode()) && m.getInReplyTo() == null
            ? "The original email has no valid Message-ID. This reply can be sent, but the recipient's mail app may not join it to the same conversation."
            : null,
        m.getFromAddress(),
        m.getSentAt());
  }

  private static List<MailAddress> addresses(List<OutboundRecipient> rows, String type) {
    return rows.stream()
        .filter(r -> type.equals(r.getRecipientType()))
        .map(r -> new MailAddress(r.getEmail(), r.getDisplayName()))
        .toList();
  }

  @Transactional
  DraftResponse queue(UUID id, String key) {
    OutboundMessage m = owned(id);
    if (key.length() > 100)
      throw new ApiException(400, "MAIL_IDEMPOTENCY_INVALID", "Idempotency key is too long");
    if (!"DRAFT".equals(m.getStatus())) {
      if (key.equals(m.getIdempotencyKey())) return view(m);
      throw new ApiException(
          409, "MAIL_ALREADY_SUBMITTED", "This message has already been submitted");
    }
    var existingKey =
        messages.findByTenantIdAndCreatedByAndIdempotencyKey(
            m.getTenantId(), m.getCreatedBy(), key);
    if (existingKey.isPresent() && !existingKey.get().getId().equals(m.getId()))
      throw new ApiException(409, "MAIL_IDEMPOTENCY_REUSED", "This send key was already used");
    AppUser u = user();
    MailConnection connection =
        connections
            .findByTenantIdAndUserIdAndActiveTrue(u.getTenantId(), u.getId())
            .orElseThrow(
                () ->
                    new ApiException(
                        409, "MAIL_NOT_CONNECTED", "Connect a mailbox before sending"));
    if (!"IMAP_SMTP".equals(connection.getProvider()))
      throw new ApiException(
          409, "MAIL_SEND_UNAVAILABLE", "SMTP sending requires an IMAP/SMTP connection");
    boolean unboundStandaloneDraft =
        m.getConnectionId() == null
            && "NEW".equals(m.getComposeMode())
            && m.getReplyToMessageId() == null
            && m.getReplyToOutboundMessageId() == null;
    if (!unboundStandaloneDraft && !connection.getId().equals(m.getConnectionId()))
      throw new ApiException(
          409, "MAILBOX_CHANGED", "Reconnect the draft's original mailbox before sending");
    if (m.getQuotationId() != null) {
      Quotation q =
          quotes
              .lockForMailSubmission(m.getQuotationId(), u.getTenantId())
              .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
      if (!"READY_TO_SEND".equals(q.getStatus()) && !"SENT".equals(q.getStatus()))
        throw new ApiException(409, "QUOTE_NOT_FINALIZED", "Finalize the quotation before sending");
      if ("READY_TO_SEND".equals(q.getStatus())) {
        if (messages.existsByTenantIdAndQuotationIdAndQuotationSendKindAndStatusIn(
            u.getTenantId(),
            q.getId(),
            "PRIMARY",
            List.of("QUEUED", "SENDING", "SENT", "NEEDS_REVIEW")))
          throw new ApiException(409, "QUOTE_ALREADY_SUBMITTED", "Quotation is already being sent");
        m.setQuotationSendKind("PRIMARY");
      } else {
        m.setQuotationSendKind("FOLLOW_UP");
      }
    }
    List<OutboundRecipient> rows = recipients.findByTenantIdAndMessageId(u.getTenantId(), id);
    if (rows.stream().noneMatch(r -> "TO".equals(r.getRecipientType())))
      throw new ApiException(400, "MAIL_RECIPIENT_REQUIRED", "Add a To recipient");
    if (m.getSubject().isBlank() || m.getBodyText().isBlank())
      throw new ApiException(400, "MAIL_CONTENT_REQUIRED", "Subject and body are required");
    resolveAutoCc(m, rows);
    if ("NEW".equals(m.getComposeMode())) {
      m.setThreadId(
          threads
              .create(u.getTenantId(), connection.getId(), m.getSubject(), m.getCustomerId())
              .getId());
    } else {
      if (m.getReplyToOutboundMessageId() != null) {
        OutboundMessage original = sentReplySource(m);
        applySentReplyHeaders(m, original);
        if (m.getThreadId() == null) m.setThreadId(original.getThreadId());
      } else {
        EmailMessage original = replySource(m);
        applyReplyHeaders(m, original);
        if (m.getThreadId() == null) m.setThreadId(threads.resolveInbound(original).getId());
      }
    }
    m.setConnectionId(connection.getId());
    m.setFromAddress(connection.getSmtpFrom());
    m.setReplyTo(connection.getEmailAddress());
    m.setMessageId(
        "<"
            + m.getId()
            + "@"
            + connection.getEmailAddress().substring(connection.getEmailAddress().indexOf('@') + 1)
            + ">");
    m.setIdempotencyKey(key);
    m.setStatus("QUEUED");
    m.setQueuedAt(Instant.now());
    messages.save(m);
    return view(m);
  }

  @Transactional
  DraftResponse reconcile(UUID id, ReconcileMailRequest request) {
    OutboundMessage message = owned(id);
    if (!"NEEDS_REVIEW".equals(message.getStatus()))
      throw new ApiException(
          409, "MAIL_RECONCILIATION_INVALID", "Only uncertain submissions can be reconciled");
    if (request.note() == null || request.note().isBlank() || request.note().length() > 2000)
      throw new ApiException(
          400, "MAIL_RECONCILIATION_NOTE_REQUIRED", "Add a brief reconciliation note");
    switch (request.resolution()) {
      case "CONFIRMED_SUBMITTED" -> {
        message.setStatus("SENT");
        message.setSentAt(Instant.now());
        message.setErrorCode(null);
        if (message.getQuotationId() != null && "PRIMARY".equals(message.getQuotationSendKind())) {
          Quotation quotation =
              quotes
                  .lockForMailSubmission(message.getQuotationId(), message.getTenantId())
                  .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
          if ("READY_TO_SEND".equals(quotation.getStatus())) {
            quotation.setStatus("SENT");
            quotation.setSentAt(message.getSentAt());
            quotes.save(quotation);
          }
        }
      }
      case "CONFIRMED_NOT_SUBMITTED" -> message.setStatus("FAILED");
      default ->
          throw new ApiException(400, "MAIL_RECONCILIATION_INVALID", "Choose a valid resolution");
    }
    message.setReconciledBy(user().getId());
    message.setReconciledAt(Instant.now());
    message.setReconciliationNote(request.note().trim());
    messages.save(message);
    return view(message);
  }

  private void resolveAutoCc(OutboundMessage m, List<OutboundRecipient> existing) {
    if (m.getCustomerId() == null) return;
    Set<String> seen = new HashSet<>();
    existing.forEach(r -> seen.add(r.getEmail().toLowerCase(Locale.ROOT)));
    List<String> auto = new ArrayList<>();
    for (CustomerContact c :
        contacts.findByTenantIdAndCustomerIdAndAutoCcTrueAndActiveTrue(
            m.getTenantId(), m.getCustomerId())) auto.add(c.getEmail());
    for (CustomerExtraCc c :
        extraCc.findByTenantIdAndCustomerIdAndActiveTrue(m.getTenantId(), m.getCustomerId()))
      auto.add(c.getEmail());
    for (String value : auto) {
      String email = value.trim().toLowerCase(Locale.ROOT);
      if (!seen.add(email)) continue;
      OutboundRecipient recipient = new OutboundRecipient();
      recipient.setTenantId(m.getTenantId());
      recipient.setMessageId(m.getId());
      recipient.setRecipientType("CC");
      recipient.setEmail(email);
      recipients.save(recipient);
    }
  }
}
