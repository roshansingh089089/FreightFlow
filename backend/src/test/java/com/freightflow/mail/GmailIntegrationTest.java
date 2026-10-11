package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.GmailOauthState;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.GmailOauthStateRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class GmailIntegrationTest {
  private final GmailOauthStateRepo states = mock(GmailOauthStateRepo.class);
  private final MailConnectionRepo connections = mock(MailConnectionRepo.class);
  private final EmailMessageRepo messages = mock(EmailMessageRepo.class);
  private final GmailIntegration gmail =
      new GmailIntegration(
          states,
          connections,
          messages,
          new ObjectMapper(),
          new MailCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
          "client",
          "secret",
          "http://localhost:8080/api/integrations/gmail/callback");

  @Test
  void oauthStateIsBoundToUserAndTenantAndExpires() {
    AppUser owner = user(UUID.randomUUID(), UUID.randomUUID());
    String url = gmail.connect(owner);
    verify(states)
        .save(
            argThat(
                s ->
                    s.getTenantId().equals(owner.getTenantId())
                        && s.getUserId().equals(owner.getId())
                        && s.getStateHash().length() == 64
                        && s.getExpiresAt().isAfter(Instant.now())));
    GmailOauthState state = new GmailOauthState();
    state.setTenantId(owner.getTenantId());
    state.setUserId(owner.getId());
    state.setExpiresAt(Instant.now().plusSeconds(60));
    when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
    String raw =
        java.net.URLDecoder.decode(
            url.substring(url.indexOf("state=") + 6), java.nio.charset.StandardCharsets.UTF_8);
    ApiException mismatch =
        assertThrows(
            ApiException.class,
            () -> gmail.callback(user(owner.getTenantId(), UUID.randomUUID()), raw, "code"));
    assertEquals("GMAIL_OAUTH_STATE", mismatch.code);
    verify(states, never()).delete(any());
    state.setExpiresAt(Instant.now().minusSeconds(1));
    assertEquals(
        "GMAIL_OAUTH_STATE",
        assertThrows(ApiException.class, () -> gmail.callback(owner, raw, "code")).code);
  }

  @Test
  void messageDetailAndListUseAuthenticatedTenantAndConnection() {
    AppUser user = user(UUID.randomUUID(), UUID.randomUUID());
    MailConnection connection = new MailConnection();
    connection.setTenantId(user.getTenantId());
    connection.setUserId(user.getId());
    when(connections.findByTenantIdAndUserIdAndProvider(
            user.getTenantId(), user.getId(), "GMAIL_API"))
        .thenReturn(Optional.of(connection));
    when(messages.findByTenantIdAndConnectionIdOrderByReceivedAtDesc(
            eq(user.getTenantId()), eq(connection.getId()), any(Pageable.class)))
        .thenReturn(List.of());
    assertTrue(gmail.list(user).isEmpty());
    UUID id = UUID.randomUUID();
    assertEquals(
        "MAIL_NOT_FOUND", assertThrows(ApiException.class, () -> gmail.detail(user, id)).code);
    verify(messages).findByIdAndTenantIdAndConnectionId(id, user.getTenantId(), connection.getId());
  }

  private static AppUser user(UUID tenant, UUID id) {
    AppUser user = new AppUser();
    user.setTenantId(tenant);
    user.setId(id);
    return user;
  }
}
