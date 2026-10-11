package com.freightflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.freightflow.auth.controller.AuthController;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.tenant.repository.TenantRepo;
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
      "app.cookie-secure=false"
    })
@Import(SecurityConfig.class)
class CsrfWebTest {
  @Autowired MockMvc mvc;
  @MockitoBean TenantRepo tenants;
  @MockitoBean UserRepo users;

  @Test
  void csrfTokenCanBeFetchedButWriteWithoutTokenIsForbidden() throws Exception {
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
    mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
  }
}
