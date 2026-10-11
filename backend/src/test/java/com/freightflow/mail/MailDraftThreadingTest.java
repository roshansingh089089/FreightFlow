package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerExtraCcRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.entity.MailThread;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MailDraftThreadingTest {
  private final OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
  private final OutboundRecipientRepo recipients = mock(OutboundRecipientRepo.class);
  private final OutboundAttachmentRepo attachments = mock(OutboundAttachmentRepo.class);
  private final MailConnectionRepo connections = mock(MailConnectionRepo.class);
  private final EmailMessageRepo inbound = mock(EmailMessageRepo.class);
  private final MailThreadService threads = mock(MailThreadService.class);
  private final QuoteRepo quotes = mock(QuoteRepo.class);
  private final OutboundMailService service =
      new OutboundMailService(
          messages,
          recipients,
          attachments,
          mock(CustomerRepo.class),
          mock(ContactRepo.class),
          mock(CustomerExtraCcRepo.class),
          quotes,
          connections,
          inbound,
          threads);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private AppUser user() {
    AppUser user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    return user;
  }

  private MailConnection connection(AppUser user) {
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(user.getTenantId());
    connection.setUserId(user.getId());
    connection.setProvider("IMAP_SMTP");
    connection.setStatus("CONNECTED");
    connection.setActive(true);
    connection.setEmailAddress("agent@example.com");
    connection.setSmtpFrom("agent@example.com");
    when(connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    return connection;
  }

  private OutboundMessage standalone(AppUser user) {
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(user.getTenantId());
    message.setCreatedBy(user.getId());
    message.setComposeMode("NEW");
    message.setSubject("Freight rates");
    message.setBodyText("Please review the rates.");
    when(messages.findByIdAndTenantIdAndCreatedBy(
            message.getId(), user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(message));
    OutboundRecipient recipient = new OutboundRecipient();
    recipient.setRecipientType("TO");
    recipient.setEmail("customer@example.com");
    when(recipients.findByTenantIdAndMessageId(user.getTenantId(), message.getId()))
        .thenReturn(List.of(recipient));
    when(attachments.findByTenantIdAndMessageId(user.getTenantId(), message.getId()))
        .thenReturn(List.of());
    return message;
  }

  @Test
  void quotationDraftUsesBoundMailboxAndPreservesPdfWhenQueued() {
    AppUser owner = user();
    MailConnection mailbox = connection(owner);
    UUID quoteId = UUID.randomUUID();
    Quotation quote = new Quotation();
    quote.setId(quoteId);
    quote.setTenantId(owner.getTenantId());
    quote.setStatus("READY_TO_SEND");
    when(quotes.findByIdAndTenantId(quoteId, owner.getTenantId())).thenReturn(Optional.of(quote));
    when(quotes.lockForMailSubmission(quoteId, owner.getTenantId())).thenReturn(Optional.of(quote));

    OutboundAttachment pdf = new OutboundAttachment();
    pdf.setTenantId(owner.getTenantId());
    pdf.setUploadedBy(owner.getId());
    pdf.setFilename("QT-1.pdf");
    pdf.setMediaType("application/pdf");
    MailAttachmentService generated = mock(MailAttachmentService.class);
    byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(generated.storeGeneratedPdf("QT-1.pdf", bytes)).thenReturn(pdf);
    when(attachments.findByIdAndTenantId(pdf.getId(), owner.getTenantId()))
        .thenReturn(Optional.of(pdf));
    when(attachments.findByTenantIdAndMessageId(eq(owner.getTenantId()), any()))
        .thenAnswer(
            call -> call.getArgument(1).equals(pdf.getMessageId()) ? List.of(pdf) : List.of());

    List<OutboundRecipient> savedRecipients = new ArrayList<>();
    when(recipients.save(any(OutboundRecipient.class)))
        .thenAnswer(
            call -> {
              OutboundRecipient row = call.getArgument(0);
              savedRecipients.add(row);
              return row;
            });
    when(recipients.findByTenantIdAndMessageId(eq(owner.getTenantId()), any()))
        .thenAnswer(
            call ->
                savedRecipients.stream()
                    .filter(row -> row.getMessageId().equals(call.getArgument(1)))
                    .toList());
    java.util.Map<UUID, OutboundMessage> savedMessages = new java.util.HashMap<>();
    when(messages.save(any(OutboundMessage.class)))
        .thenAnswer(
            call -> {
              OutboundMessage message = call.getArgument(0);
              savedMessages.put(message.getId(), message);
              return message;
            });
    when(messages.findByIdAndTenantIdAndCreatedBy(
            any(), eq(owner.getTenantId()), eq(owner.getId())))
        .thenAnswer(call -> Optional.ofNullable(savedMessages.get(call.getArgument(0))));
    MailThread thread = new MailThread();
    thread.setId(UUID.randomUUID());
    when(threads.create(eq(owner.getTenantId()), eq(mailbox.getId()), anyString(), isNull()))
        .thenReturn(thread);

    UUID draftId =
        new QuotationMailDraftService(service, generated)
            .create(
                quoteId,
                null,
                "QT-1.pdf",
                bytes,
                "buyer@example.com",
                "",
                "Quotation QT-1",
                "Please find the quotation attached.",
                null);
    OutboundMessage draft = savedMessages.get(draftId);
    assertEquals(mailbox.getId(), draft.getConnectionId());
    assertEquals("NEW", draft.getComposeMode());
    assertEquals(quoteId, draft.getQuotationId());
    assertEquals("QT-1.pdf", pdf.getFilename());
    assertEquals(draftId, pdf.getMessageId());
    assertEquals(
        List.of("buyer@example.com"),
        savedRecipients.stream().map(OutboundRecipient::getEmail).toList());

    DraftResponse queued = service.queue(draftId, "quotation-key");
    assertEquals("QUEUED", queued.status());
    assertEquals("PRIMARY", draft.getQuotationSendKind());
    assertEquals("Quotation QT-1", queued.subject());
    assertEquals("Please find the quotation attached.", queued.bodyText());
    assertEquals(List.of(pdf.getId()), queued.attachmentIds());
    assertEquals(draftId, service.queue(draftId, "quotation-key").id());
    verify(threads, times(1)).create(any(), any(), any(), any());
    verify(generated).storeGeneratedPdf("QT-1.pdf", bytes);
  }

  @Test
  void newDraftBindsConnectedSmtpMailboxAndQueuesOnlyOnce() {
    AppUser owner = user();
    MailConnection mailbox = connection(owner);
    when(recipients.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    DraftResponse draft =
        service.create(
            new DraftRequest(
                null, null, List.of(), List.of(), List.of(), "Rates", "Body", List.of(), null,
                null));
    assertEquals(mailbox.getId(), draft.connectionId());
    var saved = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(saved.capture());
    assertEquals(mailbox.getId(), saved.getValue().getConnectionId());

    OutboundMessage message = standalone(owner);
    message.setConnectionId(mailbox.getId());
    MailThread thread = new MailThread();
    thread.setId(UUID.randomUUID());
    when(threads.create(owner.getTenantId(), mailbox.getId(), message.getSubject(), null))
        .thenReturn(thread);
    DraftResponse queued = service.queue(message.getId(), "same-key");
    assertEquals("QUEUED", queued.status());
    assertEquals(mailbox.getId(), queued.connectionId());
    assertEquals("same-key", message.getIdempotencyKey());
    assertNotNull(message.getQueuedAt());
    assertEquals(queued.id(), service.queue(message.getId(), "same-key").id());
    verify(threads, times(1)).create(any(), any(), any(), any());
  }

  @Test
  void legacyStandaloneDraftBindsAtQueueTime() {
    AppUser owner = user();
    MailConnection mailbox = connection(owner);
    OutboundMessage message = standalone(owner);
    MailThread thread = new MailThread();
    thread.setId(UUID.randomUUID());
    when(threads.create(owner.getTenantId(), mailbox.getId(), message.getSubject(), null))
        .thenReturn(thread);
    assertNull(message.getConnectionId());
    assertEquals("QUEUED", service.queue(message.getId(), "legacy-key").status());
    assertEquals(mailbox.getId(), message.getConnectionId());
    assertEquals("QUEUED", message.getStatus());
    verify(messages).save(message);
  }

  @Test
  void legacyQuotationDraftWithNullMailboxQueuesOnCurrentSmtpConnection() {
    AppUser owner = user();
    MailConnection mailbox = connection(owner);
    OutboundMessage message = standalone(owner);
    UUID quoteId = UUID.randomUUID();
    message.setQuotationId(quoteId);
    Quotation quote = new Quotation();
    quote.setId(quoteId);
    quote.setTenantId(owner.getTenantId());
    quote.setStatus("READY_TO_SEND");
    when(quotes.lockForMailSubmission(quoteId, owner.getTenantId())).thenReturn(Optional.of(quote));
    MailThread thread = new MailThread();
    thread.setId(UUID.randomUUID());
    when(threads.create(owner.getTenantId(), mailbox.getId(), message.getSubject(), null))
        .thenReturn(thread);

    DraftResponse queued = service.queue(message.getId(), "legacy-quotation-key");

    assertEquals("QUEUED", queued.status());
    assertEquals(quoteId, queued.quotationId());
    assertEquals(mailbox.getId(), queued.connectionId());
    assertEquals("PRIMARY", message.getQuotationSendKind());
    assertEquals("legacy-quotation-key", message.getIdempotencyKey());
    verify(messages).save(message);
  }

  @Test
  void newDraftCanBeSavedWithoutMailboxButCannotBeSentUntilConnected() {
    AppUser owner = user();
    when(recipients.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    DraftResponse draft =
        service.create(
            new DraftRequest(
                null, null, List.of(), List.of(), List.of(), "Rates", "Body", List.of(), null,
                null));
    assertNull(draft.connectionId());
    OutboundMessage message = standalone(owner);
    var error =
        assertThrows(
            com.freightflow.common.exception.ApiException.class,
            () -> service.queue(message.getId(), "no-mailbox"));
    assertEquals("MAIL_NOT_CONNECTED", error.code);
    assertEquals("DRAFT", message.getStatus());
  }

  @Test
  void activeGmailApiCannotSendOrBindNewDraft() {
    AppUser owner = user();
    MailConnection mailbox = connection(owner);
    mailbox.setProvider("GMAIL_API");
    when(recipients.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    DraftResponse draft =
        service.create(
            new DraftRequest(
                null, null, List.of(), List.of(), List.of(), "Rates", "Body", List.of(), null,
                null));
    assertNull(draft.connectionId());
    OutboundMessage message = standalone(owner);
    var error =
        assertThrows(
            com.freightflow.common.exception.ApiException.class,
            () -> service.queue(message.getId(), "gmail-key"));
    assertEquals("MAIL_SEND_UNAVAILABLE", error.code);
    assertEquals("DRAFT", message.getStatus());
  }

  @Test
  void boundStandaloneDraftCannotSwitchMailboxButSameConnectionCanReconnect() {
    AppUser owner = user();
    MailConnection original = connection(owner);
    OutboundMessage message = standalone(owner);
    message.setConnectionId(original.getId());
    MailConnection other = connection(owner);
    var error =
        assertThrows(
            com.freightflow.common.exception.ApiException.class,
            () -> service.queue(message.getId(), "switched-key"));
    assertEquals("MAILBOX_CHANGED", error.code);
    assertEquals("DRAFT", message.getStatus());
    verify(messages, never()).save(message);

    when(connections.findByTenantIdAndUserIdAndActiveTrue(owner.getTenantId(), owner.getId()))
        .thenReturn(Optional.empty());
    assertEquals(
        "MAIL_NOT_CONNECTED",
        assertThrows(
                com.freightflow.common.exception.ApiException.class,
                () -> service.queue(message.getId(), "disconnected-key"))
            .code);
    when(connections.findByTenantIdAndUserIdAndActiveTrue(owner.getTenantId(), owner.getId()))
        .thenReturn(Optional.of(original));
    MailThread thread = new MailThread();
    thread.setId(UUID.randomUUID());
    when(threads.create(owner.getTenantId(), original.getId(), message.getSubject(), null))
        .thenReturn(thread);
    assertEquals("QUEUED", service.queue(message.getId(), "reconnected-key").status());
    assertEquals(original.getId(), message.getConnectionId());
  }

  @Test
  void nullConnectionNeverRebindsReplyMetadata() {
    AppUser owner = user();
    connection(owner);
    OutboundMessage message = standalone(owner);
    message.setComposeMode("REPLY");
    message.setReplyToMessageId(UUID.randomUUID());
    assertEquals(
        "MAILBOX_CHANGED",
        assertThrows(
                com.freightflow.common.exception.ApiException.class,
                () -> service.queue(message.getId(), "unbound-reply"))
            .code);
    assertNull(message.getConnectionId());
    assertEquals("DRAFT", message.getStatus());
  }

  @Test
  void draftLookupRequiresCurrentTenantAndCreator() {
    AppUser owner = user();
    OutboundMessage foreign = new OutboundMessage();
    foreign.setTenantId(UUID.randomUUID());
    foreign.setCreatedBy(UUID.randomUUID());
    assertEquals(
        "MAIL_DRAFT_NOT_FOUND",
        assertThrows(
                com.freightflow.common.exception.ApiException.class,
                () -> service.queue(foreign.getId(), "foreign-key"))
            .code);
    verify(messages)
        .findByIdAndTenantIdAndCreatedBy(foreign.getId(), owner.getTenantId(), owner.getId());
    verifyNoInteractions(connections);
  }

  @Test
  void newDraftCannotInheritCallerSuppliedReplyHeaders() {
    user();
    when(recipients.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    DraftResponse draft =
        service.create(
            new DraftRequest(
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "New thread",
                "Hello",
                List.of(),
                "<forged@example.com>",
                "<forged@example.com>"));
    assertEquals("NEW", draft.composeMode());
    assertNull(draft.threadId());
    var saved = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(saved.capture());
    assertNull(saved.getValue().getInReplyTo());
    assertNull(saved.getValue().getReferenceHeaders());
  }

  @Test
  void replyAllPreservesReferencesAndExcludesOwnAndDuplicateAddresses() {
    AppUser user = user();
    UUID connectionId = UUID.randomUUID(), threadId = UUID.randomUUID();
    MailConnection connection = new MailConnection();
    connection.setId(connectionId);
    connection.setTenantId(user.getTenantId());
    connection.setEmailAddress("agent@example.com");
    connection.setSmtpFrom("agent@example.com");
    when(connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    EmailMessage original = new EmailMessage();
    original.setTenantId(user.getTenantId());
    original.setConnectionId(connectionId);
    original.setThreadId(threadId);
    original.setRfcMessageId("<customer-2@example.com>");
    original.setReferenceHeaders("<customer-1@example.com>");
    original.setFromEmail("customer@example.com");
    original.setReplyToEmail("customer@example.com");
    original.setToEmail("agent@example.com, copy@example.com");
    original.setCcEmail("copy@example.com, manager@example.com");
    original.setSubject("Freight enquiry");
    when(inbound.findByIdAndTenantIdAndConnectionId(
            original.getId(), user.getTenantId(), connectionId))
        .thenReturn(Optional.of(original));
    MailThread thread = new MailThread();
    thread.setId(threadId);
    when(threads.owned(user, connection, threadId)).thenReturn(thread);
    List<OutboundRecipient> saved = new ArrayList<>();
    when(recipients.save(any(OutboundRecipient.class)))
        .thenAnswer(
            call -> {
              OutboundRecipient row = call.getArgument(0);
              saved.add(row);
              return row;
            });
    when(recipients.findByTenantIdAndMessageId(eq(user.getTenantId()), any()))
        .thenAnswer(call -> saved);
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    DraftResponse draft = service.createReply(original.getId(), true);
    assertEquals("REPLY_ALL", draft.composeMode());
    assertEquals(threadId, draft.threadId());
    assertEquals(
        List.of("customer@example.com", "copy@example.com"),
        draft.to().stream().map(MailAddress::email).toList());
    assertEquals(
        List.of("manager@example.com"), draft.cc().stream().map(MailAddress::email).toList());
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    assertEquals("<customer-2@example.com>", captured.getValue().getInReplyTo());
    assertEquals(
        "<customer-1@example.com> <customer-2@example.com>",
        captured.getValue().getReferenceHeaders());
    assertEquals("Re: Freight enquiry", captured.getValue().getSubject());
    assertNull(draft.threadingWarning());
  }

  private record ReplyFixture(
      AppUser user,
      MailConnection connection,
      EmailMessage original,
      List<OutboundRecipient> saved) {}

  private ReplyFixture replyFixture(String messageId, String references) {
    AppUser user = user();
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(user.getTenantId());
    connection.setUserId(user.getId());
    connection.setProvider("IMAP_SMTP");
    connection.setEmailAddress("agent@example.com");
    connection.setSmtpFrom("agent@example.com");
    when(connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    EmailMessage original = new EmailMessage();
    original.setTenantId(user.getTenantId());
    original.setConnectionId(connection.getId());
    original.setThreadId(UUID.randomUUID());
    original.setRfcMessageId(messageId);
    original.setReferenceHeaders(references);
    original.setFromEmail("customer@example.com");
    original.setSubject("Freight enquiry");
    when(inbound.findByIdAndTenantIdAndConnectionId(
            original.getId(), user.getTenantId(), connection.getId()))
        .thenReturn(Optional.of(original));
    MailThread thread = new MailThread();
    thread.setId(original.getThreadId());
    when(threads.owned(user, connection, original.getThreadId())).thenReturn(thread);
    List<OutboundRecipient> saved = new ArrayList<>();
    when(recipients.save(any(OutboundRecipient.class)))
        .thenAnswer(
            call -> {
              OutboundRecipient row = call.getArgument(0);
              saved.add(row);
              return row;
            });
    when(recipients.findByTenantIdAndMessageId(eq(user.getTenantId()), any()))
        .thenAnswer(call -> saved);
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    return new ReplyFixture(user, connection, original, saved);
  }

  @Test
  void validMessageIdCreatesThreadedReplyWithoutSending() {
    ReplyFixture fixture = replyFixture("<parent@example.com>", null);
    DraftResponse draft = service.createReply(fixture.original().getId(), false);
    assertEquals("DRAFT", draft.status());
    assertEquals("Re: Freight enquiry", draft.subject());
    assertEquals(
        List.of("customer@example.com"), draft.to().stream().map(MailAddress::email).toList());
    assertNull(draft.threadingWarning());
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    assertEquals("<parent@example.com>", captured.getValue().getInReplyTo());
    assertEquals("<parent@example.com>", captured.getValue().getReferenceHeaders());
    assertEquals("DRAFT", captured.getValue().getStatus());
  }

  @Test
  void missingMessageIdCreatesReplyWithoutFabricatedHeaders() {
    ReplyFixture fixture = replyFixture(null, null);
    fixture.original().setProviderMessageId("uid:123:4");
    DraftResponse draft = service.createReply(fixture.original().getId(), false);
    assertEquals("DRAFT", draft.status());
    assertEquals("Re: Freight enquiry", draft.subject());
    assertEquals(
        List.of("customer@example.com"), draft.to().stream().map(MailAddress::email).toList());
    assertNotNull(draft.threadingWarning());
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    assertNull(captured.getValue().getInReplyTo());
    assertNull(captured.getValue().getReferenceHeaders());
    assertEquals(fixture.original().getThreadId(), captured.getValue().getThreadId());
  }

  @Test
  void malformedMessageIdKeepsOnlyValidAncestorReferences() {
    ReplyFixture fixture =
        replyFixture("not-an-rfc-id <embedded@example.com>", "garbage <ancestor@example.com>");
    DraftResponse draft = service.createReply(fixture.original().getId(), false);
    assertNotNull(draft.threadingWarning());
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    assertNull(captured.getValue().getInReplyTo());
    assertEquals("<ancestor@example.com>", captured.getValue().getReferenceHeaders());
  }

  @Test
  void crossTenantReplyCannotLoadOriginalOrCreateDraft() {
    AppUser user = user();
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(user.getTenantId());
    when(connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    UUID foreignMessageId = UUID.randomUUID();
    var error =
        assertThrows(
            com.freightflow.common.exception.ApiException.class,
            () -> service.createReply(foreignMessageId, false));
    assertEquals("MAIL_NOT_FOUND", error.code);
    verify(inbound)
        .findByIdAndTenantIdAndConnectionId(
            foreignMessageId, user.getTenantId(), connection.getId());
    verify(messages, never()).save(any());
  }

  @Test
  void replyDraftRequiresExplicitQueueRequestBeforeSending() {
    ReplyFixture fixture = replyFixture(null, null);
    DraftResponse draft = service.createReply(fixture.original().getId(), false);
    assertEquals("DRAFT", draft.status());
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    OutboundMessage message = captured.getValue();
    message.setBodyText("Our reply");
    when(messages.findByIdAndTenantIdAndCreatedBy(
            message.getId(), fixture.user().getTenantId(), fixture.user().getId()))
        .thenReturn(Optional.of(message));
    DraftResponse queued = service.queue(message.getId(), "explicit-send-key");
    assertEquals("QUEUED", queued.status());
    assertNull(message.getInReplyTo());
    assertNull(message.getReferenceHeaders());
    assertNotNull(message.getMessageId());
    assertEquals(fixture.connection().getId(), message.getConnectionId());
    DraftResponse repeated = service.queue(message.getId(), "explicit-send-key");
    assertEquals(queued.id(), repeated.id());
    assertEquals("QUEUED", repeated.status());
    verify(messages, times(2)).save(any(OutboundMessage.class));
  }

  @Test
  void replyDraftCannotSendAfterActiveMailboxChanges() {
    ReplyFixture fixture = replyFixture("<parent@example.com>", null);
    DraftResponse draft = service.createReply(fixture.original().getId(), false);
    var captured = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(captured.capture());
    OutboundMessage message = captured.getValue();
    message.setBodyText("Our reply");
    when(messages.findByIdAndTenantIdAndCreatedBy(
            message.getId(), fixture.user().getTenantId(), fixture.user().getId()))
        .thenReturn(Optional.of(message));
    MailConnection other = new MailConnection();
    other.setId(UUID.randomUUID());
    other.setTenantId(fixture.user().getTenantId());
    other.setUserId(fixture.user().getId());
    other.setProvider("IMAP_SMTP");
    when(connections.findByTenantIdAndUserIdAndActiveTrue(
            fixture.user().getTenantId(), fixture.user().getId()))
        .thenReturn(Optional.of(other));

    var error =
        assertThrows(
            com.freightflow.common.exception.ApiException.class,
            () -> service.queue(draft.id(), "different-mailbox"));
    assertEquals("MAILBOX_CHANGED", error.code);
    assertEquals("DRAFT", message.getStatus());
  }

  @Test
  void sentMessageReplyAllKeepsParentThreadRecipientsAndRfcHeaders() {
    AppUser user = user();
    MailConnection connection = new MailConnection();
    connection.setId(UUID.randomUUID());
    connection.setTenantId(user.getTenantId());
    connection.setUserId(user.getId());
    connection.setProvider("IMAP_SMTP");
    connection.setEmailAddress("agent@example.com");
    connection.setSmtpFrom("agent@example.com");
    when(connections.findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(connection));
    OutboundMessage original = new OutboundMessage();
    original.setTenantId(user.getTenantId());
    original.setConnectionId(connection.getId());
    original.setCreatedBy(user.getId());
    original.setStatus("SENT");
    original.setThreadId(UUID.randomUUID());
    original.setSubject("Rate request");
    original.setMessageId("<sent@example.com>");
    original.setReferenceHeaders("<ancestor@example.com>");
    when(messages.findByIdAndTenantIdAndConnectionIdAndStatus(
            original.getId(), user.getTenantId(), connection.getId(), "SENT"))
        .thenReturn(Optional.of(original));
    OutboundRecipient buyer = new OutboundRecipient();
    buyer.setRecipientType("TO");
    buyer.setEmail("buyer@example.com");
    OutboundRecipient own = new OutboundRecipient();
    own.setRecipientType("TO");
    own.setEmail("agent@example.com");
    OutboundRecipient manager = new OutboundRecipient();
    manager.setRecipientType("CC");
    manager.setEmail("manager@example.com");
    List<OutboundRecipient> draftRecipients = new ArrayList<>();
    when(recipients.findByTenantIdAndMessageId(eq(user.getTenantId()), any()))
        .thenAnswer(
            call ->
                original.getId().equals(call.getArgument(1))
                    ? List.of(buyer, own, manager)
                    : draftRecipients);
    when(recipients.save(any(OutboundRecipient.class)))
        .thenAnswer(
            call -> {
              OutboundRecipient row = call.getArgument(0);
              draftRecipients.add(row);
              return row;
            });
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());

    DraftResponse draft = service.createReply(original.getId(), true);
    assertEquals("DRAFT", draft.status());
    assertEquals("REPLY_ALL", draft.composeMode());
    assertEquals(original.getId(), draft.replyToOutboundMessageId());
    assertNull(draft.replyToMessageId());
    assertEquals(original.getThreadId(), draft.threadId());
    assertEquals("Re: Rate request", draft.subject());
    assertEquals(
        List.of("buyer@example.com"), draft.to().stream().map(MailAddress::email).toList());
    assertEquals(
        List.of("manager@example.com"), draft.cc().stream().map(MailAddress::email).toList());
    var saved = org.mockito.ArgumentCaptor.forClass(OutboundMessage.class);
    verify(messages).save(saved.capture());
    OutboundMessage reply = saved.getValue();
    assertEquals("<sent@example.com>", reply.getInReplyTo());
    assertEquals("<ancestor@example.com> <sent@example.com>", reply.getReferenceHeaders());
    reply.setBodyText("Our follow-up");
    when(messages.findByIdAndTenantIdAndCreatedBy(reply.getId(), user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(reply));
    DraftResponse queued = service.queue(reply.getId(), "sent-reply-key");
    assertEquals("QUEUED", queued.status());
    assertEquals(original.getThreadId(), queued.threadId());
    assertEquals("<sent@example.com>", reply.getInReplyTo());
    assertNotEquals(original.getMessageId(), reply.getMessageId());
  }

  @Test
  void ambiguousSmtpOutcomeRequiresExplicitRecordedResolution() {
    AppUser user = user();
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(user.getTenantId());
    message.setCreatedBy(user.getId());
    message.setStatus("NEEDS_REVIEW");
    when(messages.findByIdAndTenantIdAndCreatedBy(
            message.getId(), user.getTenantId(), user.getId()))
        .thenReturn(Optional.of(message));
    when(recipients.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(any(), any())).thenReturn(List.of());
    assertEquals(
        "MAIL_RECONCILIATION_NOTE_REQUIRED",
        assertThrows(
                com.freightflow.common.exception.ApiException.class,
                () ->
                    service.reconcile(
                        message.getId(), new ReconcileMailRequest("CONFIRMED_NOT_SUBMITTED", "")))
            .code);
    DraftResponse resolved =
        service.reconcile(
            message.getId(),
            new ReconcileMailRequest("CONFIRMED_NOT_SUBMITTED", "Checked provider Sent folder"));
    assertEquals("FAILED", resolved.status());
    assertNotNull(resolved.reconciledAt());
    assertEquals("Checked provider Sent folder", resolved.reconciliationNote());
    verify(messages).save(message);
  }
}
