package com.freightflow;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(expression = "#{systemEnvironment['DATABASE_URL'] != null}", loadContext = false)
class AuthLogoutIntegrationTest {
 @Autowired MockMvc mvc;

 @Test @Transactional void loginMeLogoutAndClearedCookie() throws Exception {
  String email="logout-test-"+UUID.randomUUID()+"@example.test";
  String password="test-password-123";
  mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
    .content("{\"companyName\":\"Logout Test Co\",\"fullName\":\"Test User\",\"email\":\""+email+"\",\"password\":\""+password+"\",\"country\":\"India\"}"))
    .andExpect(status().isCreated());

  var login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
    .content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"))
    .andExpect(status().isOk())
    .andExpect(header().string("Set-Cookie",containsString("HttpOnly")))
    .andReturn();
  Cookie auth=login.getResponse().getCookie("ff_session");
  assertNotNull(auth);
  mvc.perform(get("/api/auth/me").cookie(auth)).andExpect(status().isOk())
    .andExpect(jsonPath("$.email").value(email));

  mvc.perform(post("/api/auth/logout").cookie(auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isNoContent())
    .andExpect(header().string("Set-Cookie",containsString("ff_session=")))
    .andExpect(header().string("Set-Cookie",containsString("Max-Age=0")));
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
 }

 @Test void aiExtractionRequiresAuthentication() throws Exception {
  mvc.perform(post("/api/ai/shipment-extract").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"shipment from Delhi to Dubai\"}"))
    .andExpect(status().isUnauthorized());
 }

 @Test void logoutWithMissingCookieStillClearsIt() throws Exception {
  mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isNoContent())
    .andExpect(header().string("Set-Cookie",containsString("Max-Age=0")));
 }
}
