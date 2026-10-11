package com.freightflow.mail;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.freightflow.auth.entity.AppUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MailConnectionControllerWebTest {
  private final MailConnectionService service = mock(MailConnectionService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    AppUser user = new AppUser();
    user.setId(UUID.randomUUID());
    user.setTenantId(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    mvc = MockMvcBuilders.standaloneSetup(new MailConnectionController(service)).build();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void currentReturnsNoContentWithoutConnection() throws Exception {
    mvc.perform(get("/api/mail/connections/current")).andExpect(status().isNoContent());
  }

  @Test
  void savingConnectionKeepsRequestAndResponseFields() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.save(any(), any()))
        .thenReturn(
            new MailConnectionController.View(
                id,
                "IMAP_SMTP",
                "agent@example.com",
                "CONNECTED",
                true,
                null,
                "imap.example.com",
                993,
                "IMPLICIT_TLS",
                "smtp.example.com",
                465,
                "IMPLICIT_TLS"));
    mvc.perform(
            post("/api/mail/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"providerType":"IMAP_SMTP","emailAddress":"agent@example.com",
                     "imapHost":"imap.example.com","imapPort":993,"imapSecurityMode":"IMPLICIT_TLS",
                     "imapUsername":"agent@example.com","imapPassword":"secret",
                     "smtpHost":"smtp.example.com","smtpPort":465,"smtpSecurityMode":"IMPLICIT_TLS",
                     "smtpUsername":"agent@example.com","smtpPassword":"secret","smtpFrom":"agent@example.com"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providerType").value("IMAP_SMTP"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.emailAddress").value("agent@example.com"));
  }
}
