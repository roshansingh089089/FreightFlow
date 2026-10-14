package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.customer.repository.*;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.entity.OutboundRecipient;
import com.freightflow.mail.repository.*;
import com.freightflow.quotation.repository.QuoteRepo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class StoredMailAuthorizationTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listRequiresTenantAndCreatorAndDetailRejectsAnotherSender() {
    AppUser viewer = new AppUser();
    viewer.setId(UUID.randomUUID());
    viewer.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(viewer, null, List.of()));
    OutboundMessageRepo repo = mock(OutboundMessageRepo.class);
    when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    OutboundMailService service = service(repo);
    service.search(null, null, "", "", "", "", null, null, 0, 20);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Specification<OutboundMessage>> spec =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(spec.capture(), any(Pageable.class));
    @SuppressWarnings("unchecked")
    Root<OutboundMessage> root = mock(Root.class);
    @SuppressWarnings("unchecked")
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<UUID> tenantPath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<UUID> creatorPath = mock(Path.class);
    when(root.<UUID>get("tenantId")).thenReturn(tenantPath);
    when(root.<UUID>get("createdBy")).thenReturn(creatorPath);
    when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
    spec.getValue().toPredicate(root, query, cb);
    verify(cb).equal(tenantPath, viewer.getTenantId());
    verify(cb).equal(creatorPath, viewer.getId());

    UUID anotherSenderMessage = UUID.randomUUID();
    when(repo.findByIdAndTenantIdAndCreatedBy(
            anotherSenderMessage, viewer.getTenantId(), viewer.getId()))
        .thenReturn(Optional.empty());
    assertEquals(
        "MAIL_DRAFT_NOT_FOUND",
        assertThrows(ApiException.class, () -> service.get(anotherSenderMessage)).code);
    UUID anotherTenantMessage = UUID.randomUUID();
    assertEquals(
        "MAIL_DRAFT_NOT_FOUND",
        assertThrows(ApiException.class, () -> service.get(anotherTenantMessage)).code);
  }

  @Test
  void bccIsAvailableOnlyToTheSendingUser() {
    AppUser sender = new AppUser();
    sender.setId(UUID.randomUUID());
    sender.setTenantId(UUID.randomUUID());
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(sender.getTenantId());
    message.setCreatedBy(sender.getId());
    message.setStatus("SENT");
    OutboundMessageRepo repo = mock(OutboundMessageRepo.class);
    OutboundRecipientRepo recipients = mock(OutboundRecipientRepo.class);
    OutboundAttachmentRepo attachments = mock(OutboundAttachmentRepo.class);
    OutboundRecipient bcc = new OutboundRecipient();
    bcc.setRecipientType("BCC");
    bcc.setEmail("private@example.test");
    when(repo.findByIdAndTenantIdAndCreatedBy(
            message.getId(), sender.getTenantId(), sender.getId()))
        .thenReturn(Optional.of(message));
    when(recipients.findByTenantIdAndMessageId(sender.getTenantId(), message.getId()))
        .thenReturn(List.of(bcc));
    when(attachments.findByTenantIdAndMessageId(sender.getTenantId(), message.getId()))
        .thenReturn(List.of());
    OutboundMailService service =
        new OutboundMailService(
            repo,
            recipients,
            attachments,
            mock(CustomerRepo.class),
            mock(ContactRepo.class),
            mock(CustomerExtraCcRepo.class),
            mock(QuoteRepo.class),
            mock(MailConnectionRepo.class),
            mock(EmailMessageRepo.class),
            mock(MailThreadService.class));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(sender, null, List.of()));
    assertEquals("private@example.test", service.get(message.getId()).bcc().getFirst().email());
    AppUser coworker = new AppUser();
    coworker.setId(UUID.randomUUID());
    coworker.setTenantId(sender.getTenantId());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(coworker, null, List.of()));
    assertThrows(ApiException.class, () -> service.get(message.getId()));
    verify(recipients, times(1)).findByTenantIdAndMessageId(sender.getTenantId(), message.getId());
  }

  private static OutboundMailService service(OutboundMessageRepo repo) {
    return new OutboundMailService(
        repo,
        mock(OutboundRecipientRepo.class),
        mock(OutboundAttachmentRepo.class),
        mock(CustomerRepo.class),
        mock(ContactRepo.class),
        mock(CustomerExtraCcRepo.class),
        mock(QuoteRepo.class),
        mock(MailConnectionRepo.class),
        mock(EmailMessageRepo.class),
        mock(MailThreadService.class));
  }
}
