package com.freightflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in provider smoke test. Normal test runs never call Gemini. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="GEMINI_LIVE_TEST",matches="true")
class GeminiLiveSmokeTest {
 @Autowired GeminiClient client;
 @Autowired MockMvc mvc;
 @Autowired AiShipmentService service;
 @Test void extractsShipmentWithStructuredSchema(){
  assertTrue(client.configured());
  AiShipmentExtraction result=service.extract("Need 1 x 40HC from Laem Chabang, Thailand to Hamburg, Germany. Garments, 18,000 kg. Cargo ready 15 Oct 2026. Need pickup and destination delivery.");
  assertEquals("OCEAN",result.mode());
  assertEquals("Garments",result.commodity());
  assertEquals(1,result.containerQuantity());
  assertEquals("40HC",result.containerType());
  assertEquals(0,result.grossWeight().compareTo(new java.math.BigDecimal("18000")));
  assertEquals(Boolean.TRUE,result.pickupRequired());
  assertEquals(Boolean.TRUE,result.destinationDeliveryRequired());
  assertNull(result.insuranceRequired());
 }
 @Test @Transactional void authenticatedExtractionEndpointReturnsReviewData() throws Exception {
  String email="ai-live-"+UUID.randomUUID()+"@example.test";
  var signup=mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
   .content("{\"companyName\":\"AI Test\",\"fullName\":\"Test User\",\"email\":\""+email+"\",\"password\":\"test-password-123\",\"country\":\"India\"}"))
   .andExpect(status().isCreated()).andReturn();
  Cookie cookie=signup.getResponse().getCookie("ff_session");
  assertNotNull(cookie);
  mvc.perform(post("/api/ai/shipment-extract").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
   .content("{\"text\":\"Contact email: aajit53@gmail.com. Need 1 x 40HC from Laem Chabang, Thailand to Hamburg, Germany. Garments, 18,000 kg. Cargo ready 15 Oct 2026. Need pickup and destination delivery.\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.extracted.mode").value("OCEAN"))
   .andExpect(jsonPath("$.extracted.contactEmail").value("aajit53@gmail.com"))
   .andExpect(jsonPath("$.extracted.containerType").value("40HC"));
 }
}
