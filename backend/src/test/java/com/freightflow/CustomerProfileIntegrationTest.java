package com.freightflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(expression = "#{systemEnvironment['DATABASE_URL'] != null}", loadContext = false)
class CustomerProfileIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;

  private record Account(Cookie cookie, UUID id) {}

  private Account signup(String name) throws Exception {
    String email = "customer-profile-" + UUID.randomUUID() + "@example.test";
    JsonNode response =
        mapper.readTree(
            mvc.perform(
                    post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            mapper.writeValueAsString(
                                Map.of(
                                    "companyName",
                                    name,
                                    "fullName",
                                    "Agent",
                                    "email",
                                    email,
                                    "password",
                                    "test-password-123",
                                    "country",
                                    "India"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of("email", email, "password", "test-password-123"))))
            .andExpect(status().isOk())
            .andReturn();
    return new Account(
        login.getResponse().getCookie("ff_session"), UUID.fromString(response.get("id").asText()));
  }

  private JsonNode create(Account account, String company) throws Exception {
    return mapper.readTree(
        mvc.perform(
                post("/api/customers")
                    .cookie(account.cookie())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "companyName",
                                company,
                                "customerType",
                                "EXPORTER",
                                "country",
                                "India",
                                "primaryContact",
                                Map.of(
                                    "name",
                                    "Primary",
                                    "email",
                                    "primary@example.test",
                                    "phone",
                                    "1234567890")))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  @Transactional
  void customerDuplicateContactsAutoCcAndTenantIsolation() throws Exception {
    Account first = signup("Tenant One"), second = signup("Tenant Two");
    JsonNode created = create(first, "  Acme  Freight ");
    UUID id = UUID.fromString(created.get("id").asText());
    mvc.perform(
            post("/api/customers")
                .cookie(first.cookie())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "companyName",
                            "acme freight",
                            "customerType",
                            "EXPORTER",
                            "country",
                            "India",
                            "primaryContact",
                            Map.of(
                                "name",
                                "Primary",
                                "email",
                                "primary@example.test",
                                "phone",
                                "1234567890")))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE"));
    JsonNode contact =
        mapper.readTree(
            mvc.perform(
                    post("/api/customers/{id}/contacts", id)
                        .cookie(first.cookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            mapper.writeValueAsString(
                                Map.of(
                                    "name",
                                    "Billing",
                                    "email",
                                    "billing@example.test",
                                    "phone",
                                    "1234567890",
                                    "role",
                                    "BILLING",
                                    "primary",
                                    false,
                                    "autoCc",
                                    true,
                                    "active",
                                    true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertTrue(contact.get("autoCc").asBoolean());
    mvc.perform(
            put("/api/customers/{id}/cc", id)
                .cookie(first.cookie())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"email\":\"accounts@example.test\",\"displayName\":\"Accounts\"}]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("accounts@example.test"));
    mvc.perform(get("/api/customers/{id}", id).cookie(second.cookie()))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/customers/{id}/contacts/{contactId}", id, contact.get("id").asText())
                .cookie(second.cookie())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "name",
                            "Altered",
                            "email",
                            "billing@example.test",
                            "phone",
                            "1234567890",
                            "primary",
                            false,
                            "autoCc",
                            true,
                            "active",
                            true))))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/customers?mine=true").cookie(first.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/customers?mine=true").cookie(second.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @Transactional
  void roleAndArchiveAreEnforced() throws Exception {
    Account account = signup("Role Tenant");
    JsonNode created = create(account, "Archive Test");
    UUID id = UUID.fromString(created.get("id").asText());
    mvc.perform(post("/api/customers/{id}/archive", id).cookie(account.cookie()).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    entityManager.flush();
    jdbc.update("update users set role='READ_ONLY' where id=?", account.id());
    entityManager.clear();
    mvc.perform(post("/api/customers/{id}/restore", id).cookie(account.cookie()).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/customers/{id}", id).cookie(account.cookie())).andExpect(status().isOk());
  }
}
