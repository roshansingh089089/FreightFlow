package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.PermissionPolicy;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerExtraCcRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import com.freightflow.mail.repository.OutboundAttachmentRepo;
import com.freightflow.mail.repository.OutboundMessageRepo;
import com.freightflow.mail.repository.OutboundRecipientRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityAndMailTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static AppUser user(String role) {
    AppUser u = new AppUser();
    u.setId(UUID.randomUUID());
    u.setTenantId(UUID.randomUUID());
    u.setRole(role);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    return u;
  }

  @Test
  void credentialsAreAuthenticatedEncryptedAndTamperingIsRejected() {
    MailCredentialCipher cipher =
        new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    String encrypted = cipher.encrypt("smtp-private-password");
    assertTrue(encrypted.startsWith("v1:"));
    assertFalse(encrypted.contains("smtp-private-password"));
    assertEquals("smtp-private-password", cipher.decrypt(encrypted));
    String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
    assertEquals(
        "MAIL_CREDENTIAL_UNAVAILABLE",
        assertThrows(ApiException.class, () -> cipher.decrypt(tampered)).code);
  }

  @Test
  void permissionsAreRoleAndActionSpecific() {
    PermissionPolicy policy = new PermissionPolicy();
    user("READ_ONLY");
    assertTrue(policy.can("customer.read"));
    assertFalse(policy.can("mail.send"));
    assertFalse(policy.can("customer.archive"));
    user("SALES");
    assertTrue(policy.can("mail.send"));
    assertTrue(policy.can("mail.manage"));
    assertFalse(policy.can("customer.archive"));
    user("ADMIN");
    assertTrue(policy.can("tenant.user.manage"));
  }

  @Test
  void anotherUsersDraftIsInvisibleAndCannotBeSent() {
    AppUser u = user("SALES");
    UUID id = UUID.randomUUID();
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundMailService service =
        new OutboundMailService(
            messages,
            mock(OutboundRecipientRepo.class),
            mock(OutboundAttachmentRepo.class),
            mock(CustomerRepo.class),
            mock(ContactRepo.class),
            mock(CustomerExtraCcRepo.class),
            mock(QuoteRepo.class),
            mock(MailConnectionRepo.class),
            mock(EmailMessageRepo.class),
            mock(MailThreadService.class));
    when(messages.findByIdAndTenantIdAndCreatedBy(id, u.getTenantId(), u.getId()))
        .thenReturn(Optional.empty());
    assertEquals(
        "MAIL_DRAFT_NOT_FOUND", assertThrows(ApiException.class, () -> service.get(id)).code);
    assertEquals(
        "MAIL_DRAFT_NOT_FOUND",
        assertThrows(ApiException.class, () -> service.queue(id, "command-1")).code);
    verify(messages, times(2)).findByIdAndTenantIdAndCreatedBy(id, u.getTenantId(), u.getId());
  }

  @Test
  void aSubmittedDraftIsIdempotentForTheSameCommand() {
    AppUser u = user("SALES");
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(u.getTenantId());
    message.setCreatedBy(u.getId());
    message.setStatus("QUEUED");
    message.setIdempotencyKey("send-1");
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundRecipientRepo recipients = mock(OutboundRecipientRepo.class);
    OutboundAttachmentRepo attachments = mock(OutboundAttachmentRepo.class);
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    when(messages.findByIdAndTenantIdAndCreatedBy(message.getId(), u.getTenantId(), u.getId()))
        .thenReturn(Optional.of(message));
    when(recipients.findByTenantIdAndMessageId(u.getTenantId(), message.getId()))
        .thenReturn(List.of());
    when(attachments.findByTenantIdAndMessageId(u.getTenantId(), message.getId()))
        .thenReturn(List.of());
    OutboundMailService service =
        new OutboundMailService(
            messages,
            recipients,
            attachments,
            mock(CustomerRepo.class),
            mock(ContactRepo.class),
            mock(CustomerExtraCcRepo.class),
            mock(QuoteRepo.class),
            connections,
            mock(EmailMessageRepo.class),
            mock(MailThreadService.class));
    assertEquals("QUEUED", service.queue(message.getId(), "send-1").status());
    assertEquals(
        "MAIL_ALREADY_SUBMITTED",
        assertThrows(ApiException.class, () -> service.queue(message.getId(), "send-2")).code);
    verifyNoInteractions(connections);
  }

  @Test
  void attachmentRejectsBinaryDisguisedAsTextAndCrossTenantDownloads() {
    AppUser u = user("SALES");
    OutboundAttachmentRepo repo = mock(OutboundAttachmentRepo.class);
    MailAttachmentStorage storage = mock(MailAttachmentStorage.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MailAttachmentStorage> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(storage);
    MailAttachmentService service = new MailAttachmentService(repo, provider);
    MockMultipartFile binary =
        new MockMultipartFile("file", "payload.txt", "text/plain", new byte[] {1, 2, 3, 0, 4});
    assertEquals(
        "MAIL_ATTACHMENT_TYPE_INVALID",
        assertThrows(ApiException.class, () -> service.upload(binary)).code);
    UUID id = UUID.randomUUID();
    when(repo.findByIdAndTenantId(id, u.getTenantId())).thenReturn(Optional.empty());
    assertEquals(
        "MAIL_ATTACHMENT_NOT_FOUND",
        assertThrows(ApiException.class, () -> service.owned(id)).code);
  }
}
