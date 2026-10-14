package com.freightflow.quotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.mail.EmailSender;
import com.freightflow.mail.QuotationMailDraftService;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.repository.EmailLogRepo;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.tenant.repository.TenantRepo;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class QuoteCommunicationControllerTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void draftCannotBeSent() {
    QuotationLookupService b = mock(QuotationLookupService.class);
    EmailSender mail = mock(EmailSender.class);
    QuoteCommunicationController c =
        new QuoteCommunicationController(
            b,
            mock(LineRepo.class),
            mock(QuoteRepo.class),
            mock(ContactRepo.class),
            mock(TenantRepo.class),
            mock(EmailLogRepo.class),
            mail,
            mock(QuotationLifecycleService.class),
            mock(QuotationMailDraftService.class),
            mock(QuotationDocumentService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID id = UUID.randomUUID();
    when(b.quote(id)).thenReturn(new Quotation());
    ApiException error =
        assertThrows(
            ApiException.class,
            () ->
                c.send(
                    id,
                    new QuoteCommunicationController.SendRequest(
                        "buyer@example.test", "", "Subject", "Body")));
    assertEquals("QUOTE_NOT_FINALIZED", error.code);
    verify(mail, never())
        .send(anyString(), anyString(), anyString(), anyString(), anyString(), any(byte[].class));
  }

  @Test
  void missingEmailConfigurationDoesNotAttemptDelivery() {
    QuotationLookupService b = mock(QuotationLookupService.class);
    QuoteRepo quotes = mock(QuoteRepo.class);
    EmailLogRepo logs = mock(EmailLogRepo.class);
    EmailSender mail = mock(EmailSender.class);
    QuoteCommunicationController c =
        new QuoteCommunicationController(
            b,
            mock(LineRepo.class),
            quotes,
            mock(ContactRepo.class),
            mock(TenantRepo.class),
            logs,
            mail,
            mock(QuotationLifecycleService.class),
            mock(QuotationMailDraftService.class),
            mock(QuotationDocumentService.class));
    AppUser user = new AppUser();
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    UUID id = UUID.randomUUID();
    Quotation quote = new Quotation();
    quote.setTenantId(user.getTenantId());
    quote.setStatus("READY_TO_SEND");
    when(b.quote(id)).thenReturn(quote);
    assertEquals(
        Map.of(),
        c.send(
            id,
            new QuoteCommunicationController.SendRequest(
                "buyer@example.test", "", "Subject", "Body")));
    verify(mail, never())
        .send(anyString(), anyString(), anyString(), anyString(), anyString(), any(byte[].class));
    verify(logs, never()).save(any());
  }

  @Test
  void previewDoesNotSendAndLegacyEndpointQueuesThroughDrafts() {
    QuotationLookupService lookup = mock(QuotationLookupService.class);
    QuotationMailDraftService drafts = mock(QuotationMailDraftService.class);
    QuoteCommunicationController controller =
        spy(
            new QuoteCommunicationController(
                lookup,
                mock(LineRepo.class),
                mock(QuoteRepo.class),
                mock(ContactRepo.class),
                mock(TenantRepo.class),
                mock(EmailLogRepo.class),
                mock(EmailSender.class),
                mock(QuotationLifecycleService.class),
                drafts,
                mock(QuotationDocumentService.class)));
    UUID id = UUID.randomUUID();
    Quotation quote = new Quotation();
    quote.setId(id);
    quote.setCustomerId(UUID.randomUUID());
    quote.setQuotationNumber("QT-2026-000001");
    quote.setStatus("READY_TO_SEND");
    when(lookup.quote(id)).thenReturn(quote);
    doReturn(new byte[] {1, 2, 3}).when(controller).render(quote);
    UUID draftId = UUID.randomUUID();
    when(drafts.submitLegacy(
            eq(id),
            eq(quote.getCustomerId()),
            eq("QT-2026-000001.pdf"),
            any(),
            eq("buyer@example.test"),
            eq(""),
            eq("Subject"),
            eq("Body"),
            eq("quotation:" + id)))
        .thenReturn(Map.of("status", "QUEUED", "draftId", draftId));
    Object result =
        controller.send(
            id,
            new QuoteCommunicationController.SendRequest(
                "buyer@example.test", "", "Subject", "Body"));
    assertEquals(Map.of("status", "QUEUED", "draftId", draftId), result);
    verify(drafts)
        .submitLegacy(
            eq(id),
            eq(quote.getCustomerId()),
            anyString(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString());
    assertEquals("READY_TO_SEND", quote.getStatus());
  }

  @Test
  void quotationDraftPassesChosenSourceEmailToMailboxScopedReplyService() {
    QuotationLookupService lookup = mock(QuotationLookupService.class);
    QuotationMailDraftService drafts = mock(QuotationMailDraftService.class);
    QuoteCommunicationController controller =
        spy(
            new QuoteCommunicationController(
                lookup,
                mock(LineRepo.class),
                mock(QuoteRepo.class),
                mock(ContactRepo.class),
                mock(TenantRepo.class),
                mock(EmailLogRepo.class),
                mock(EmailSender.class),
                mock(QuotationLifecycleService.class),
                drafts,
                mock(QuotationDocumentService.class)));
    UUID id = UUID.randomUUID(), source = UUID.randomUUID(), draftId = UUID.randomUUID();
    Quotation quote = new Quotation();
    quote.setId(id);
    quote.setCustomerId(UUID.randomUUID());
    quote.setQuotationNumber("QT-2026-000001");
    quote.setStatus("READY_TO_SEND");
    when(lookup.quote(id)).thenReturn(quote);
    doReturn(
            new QuoteCommunicationController.Preview(
                "buyer@example.test",
                "",
                "Quotation",
                "Body",
                new QuoteCommunicationController.Attachment("quote.pdf")))
        .when(controller)
        .preview(id);
    doReturn(new byte[] {1}).when(controller).render(quote);
    when(drafts.create(
            eq(id),
            eq(quote.getCustomerId()),
            anyString(),
            any(byte[].class),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(source)))
        .thenReturn(draftId);
    assertEquals(
        draftId,
        controller
            .mailDraft(
                id,
                new QuoteCommunicationController.MailDraftRequest(source, null, null, null, null))
            .get("draftId"));
    verify(drafts)
        .create(
            eq(id),
            eq(quote.getCustomerId()),
            anyString(),
            any(byte[].class),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(source));
  }
}
