package com.freightflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(expression = "#{systemEnvironment['DATABASE_URL'] != null}", loadContext = false)
class MailDraftIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  private Cookie signup() throws Exception {
    String email = "draft-" + UUID.randomUUID() + "@example.test";
    var response =
        mvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "companyName",
                                "Draft Test",
                                "fullName",
                                "Agent",
                                "email",
                                email,
                                "password",
                                "test-password-123",
                                "country",
                                "India"))))
            .andExpect(status().isCreated())
            .andReturn();
    return response.getResponse().getCookie("ff_session");
  }

  private String payload(String subject) {
    return "{\"to\":[{\"email\":\"recipient@example.test\"}],\"cc\":[],\"bcc\":[],\"subject\":\""
        + subject
        + "\",\"bodyText\":\"Hello\",\"attachmentIds\":[]}";
  }

  @Test
  void searchRequiresAnActiveMailbox() throws Exception {
    Cookie owner = signup();
    mvc.perform(get("/api/mail/search").cookie(owner))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAIL_NOT_CONNECTED"));
  }

  @Test
  void searchFindsInboundAndOutboundWithinTheActiveMailbox() throws Exception {
    Cookie owner = signup();
    var account =
        jdbc.queryForMap("select id,tenant_id from users order by created_at desc limit 1");
    UUID userId = (UUID) account.get("id"), tenantId = (UUID) account.get("tenant_id");
    UUID connectionId = UUID.randomUUID(), messageId = UUID.randomUUID();
    jdbc.update(
        "insert into mail_connections(id,tenant_id,user_id,provider,email_address,status,active,created_at,updated_at) "
            + "values (?,?,?,'IMAP_SMTP','agent@example.test','CONNECTED',true,now(),now())",
        connectionId,
        tenantId,
        userId);
    jdbc.update(
        "insert into email_messages(id,tenant_id,connection_id,provider_message_id,from_email,to_email,subject,body_text,received_at,is_read,created_at) "
            + "values (?,?,?,'uid:1:1','buyer@example.test','agent@example.test','Freight enquiry','Hello',now(),false,now())",
        messageId,
        tenantId,
        connectionId);
    mvc.perform(get("/api/mail/search?sender=buyer&direction=INBOUND").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(messageId.toString()));
    mvc.perform(get("/api/mail/search?sender=wrong").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void draftsAreEditablePrivateAndCannotSendWithoutMailbox() throws Exception {
    Cookie owner = signup(), other = signup();
    JsonNode created =
        mapper.readTree(
            mvc.perform(
                    post("/api/mail/drafts")
                        .cookie(owner)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    String id = created.get("id").asText();
    mvc.perform(get("/api/mail/drafts/{id}", id).cookie(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/mail/stored?status=DRAFT").cookie(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            put("/api/mail/drafts/{id}", id)
                .cookie(other)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("Altered")))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/mail/drafts/{id}", id)
                .cookie(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("Updated")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("Updated"));
    mvc.perform(
            post("/api/mail/drafts/{id}/send", id)
                .cookie(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"send-1\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAIL_NOT_CONNECTED"));
  }

  @Test
  void duplicateRecipientsAreRejectedBeforePersistence() throws Exception {
    Cookie owner = signup();
    String duplicate =
        "{\"to\":[{\"email\":\"ONE@example.test\"}],\"cc\":[{\"email\":\"one@example.test\"}],\"subject\":\"Hello\",\"bodyText\":\"Body\"}";
    mvc.perform(
            post("/api/mail/drafts")
                .cookie(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicate))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MAIL_RECIPIENT_DUPLICATE"));
    mvc.perform(get("/api/mail/drafts?status=DRAFT").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
