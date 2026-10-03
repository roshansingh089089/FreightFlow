package com.freightflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(expression = "#{systemEnvironment['DATABASE_URL'] != null}", loadContext = false)
class QuotationRecipientIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @MockitoBean AiModelClient model;

 @Test @Transactional void extractedContactEmailSurvivesEnquiryAndReachesQuotationModal() throws Exception {
  String employee="employee-"+UUID.randomUUID()+"@example.test";
  String signup=mapper.writeValueAsString(Map.of("companyName","Recipient Test","fullName","Agent","email",employee,"password","test-password-123","country","India"));
  var signupResponse=mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup)).andExpect(status().isCreated()).andReturn();
  Cookie auth=signupResponse.getResponse().getCookie("ff_session");
  assertNotNull(auth);
  JsonNode customer=mapper.readTree(mvc.perform(post("/api/customers").cookie(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("companyName","Buyer Co","customerType","IMPORTER","country","India","primaryContact",Map.of("name","Primary Buyer","email","primary@example.test","phone","1234567890"))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  String customerId=customer.get("id").asText();

  when(model.generate(any(),any(),any())).thenReturn(mapper.readTree("{\"contactEmail\":\"aajit53@gmail.com\",\"mode\":\"OCEAN\",\"origin\":\"Mumbai\",\"destination\":\"Singapore\",\"commodity\":\"Garments\",\"missingFields\":[],\"warnings\":[]}"));
  JsonNode extraction=mapper.readTree(mvc.perform(post("/api/ai/shipment-extract").cookie(auth).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Contact email: aajit53@gmail.com. Ship garments from Mumbai to Singapore by ocean.\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertEquals("aajit53@gmail.com",extraction.at("/extracted/contactEmail").asText());

  JsonNode extracted=extraction.get("extracted");
  JsonNode enquiry=mapper.readTree(mvc.perform(post("/api/enquiries").cookie(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("customerId",customerId,"contactEmail",extracted.get("contactEmail").asText(),"mode",extracted.get("mode").asText(),"origin",extracted.get("origin").asText(),"destination",extracted.get("destination").asText(),"commodity",extracted.get("commodity").asText())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  String enquiryId=enquiry.get("id").asText();
  JsonNode saved=mapper.readTree(mvc.perform(get("/api/enquiries/{id}",enquiryId).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertEquals("aajit53@gmail.com",saved.at("/enquiry/contactEmail").asText());

  JsonNode quotation=mapper.readTree(mvc.perform(post("/api/enquiries/{id}/quotations",enquiryId).cookie(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("currency","USD","validUntil",LocalDate.now().plusDays(7).toString(),"termsAndConditions","Standard terms","lines",new Object[]{Map.of("description","Ocean freight","quantity",1,"unitPrice",100)})))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  String quoteId=quotation.get("id").asText();
  JsonNode quoteDetail=mapper.readTree(mvc.perform(get("/api/quotations/{id}",quoteId).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertEquals("aajit53@gmail.com",quoteDetail.at("/enquiry/enquiry/contactEmail").asText());
  JsonNode preview=mapper.readTree(mvc.perform(get("/api/quotations/{id}/email-preview",quoteId).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertEquals("aajit53@gmail.com",preview.get("to").asText());
 }
}
