package com.freightflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.auth.controller.AuthController;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.tenant.repository.TenantRepo;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AuthController.class,
    properties = {
      "app.jwt-secret=01234567890123456789012345678901",
      "app.frontend-origin=http://localhost:5173",
      "app.frontend-additional-origin=https://breakfast-publishers-schools-backing.trycloudflare.com",
      "app.cookie-secure=false"
    })
@Import(SecurityConfig.class)
class CsrfWebTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean TenantRepo tenants;
  @MockitoBean UserRepo users;

  @Test
  void csrfTokenCanBeFetchedButWriteWithoutTokenIsForbidden() throws Exception {
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
    mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
  }

  @Test
  void signupFromTunnelWithValidCsrfTokenIsAllowed() throws Exception {
    var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    String token =
        json.readTree(csrfResponse.getResponse().getContentAsString()).get("token").asText();
    Cookie cookie = csrfResponse.getResponse().getCookie("XSRF-TOKEN");
    mvc.perform(
            post("/api/auth/signup")
                .header("Origin", "https://breakfast-publishers-schools-backing.trycloudflare.com")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content(
                    """
                    {"companyName":"Test Co","fullName":"Test User","email":"test@example.com", "password":"password123", "country":"IN"}
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/auth/logout")
                .header("Origin", "https://breakfast-publishers-schools-backing.trycloudflare.com")
                .cookie(cookie)
                .contentType("application/json"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/auth/logout")
                .header("Origin", "https://untrusted.example")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/auth/logout")
                .header("Origin", "http://localhost:5173")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isNoContent());
  }
}
