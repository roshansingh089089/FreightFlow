package com.freightflow.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightflow.common.exception.ApiException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiShipmentServiceTest {
  final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void parsesStructuredShipmentAndPreservesUnknowns() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String key :
        ((List<?>) AiShipmentService.schema().get("required"))
            .stream().map(String::valueOf).toList()) fields.put(key, null);
    fields.put("contactEmail", "aajit53@gmail.com");
    fields.put("mode", "ocean");
    fields.put("origin", "Laem Chabang, Thailand");
    fields.put("destination", "Hamburg, Germany");
    fields.put("shipmentType", "fcl");
    fields.put("commodity", "Garments");
    fields.put("grossWeight", 18000);
    fields.put("weightUnit", "kg");
    fields.put("containerQuantity", 1);
    fields.put("containerType", "40 HC");
    fields.put("readyDate", "2026-10-15");
    fields.put("pickupRequired", true);
    fields.put("destinationDeliveryRequired", true);
    fields.put("missingFields", List.of());
    fields.put("warnings", List.of());
    AiShipmentService service =
        new AiShipmentService(
            (instruction, input, schema) -> {
              assertTrue(instruction.contains("Unknown fields must be null"));
              assertTrue(input.contains("Laem Chabang"));
              return mapper.valueToTree(fields);
            },
            mapper);
    AiShipmentExtraction out = service.extract("Need 1 x 40HC from Laem Chabang to Hamburg");
    assertEquals("aajit53@gmail.com", out.contactEmail());
    assertEquals("OCEAN", out.mode());
    assertEquals(new BigDecimal("18000"), out.grossWeight());
    assertEquals(LocalDate.of(2026, 10, 15), out.readyDate());
    assertEquals(1, out.containerQuantity());
    assertNull(out.insuranceRequired());
    assertNull(out.incoterm());
    assertTrue(out.missingFields().isEmpty());
  }

  @Test
  void thailandToIndiaEmailToleratesModelAliasesAndFillsShipmentFields() {
    String email =
        """
        Subject: Freight Enquiry – Laem Chabang to Nhava Sheva
        Sender: roshan.singh089089@gmail.com
        Mode: FCL
        Port of Loading: Laem Chabang, Thailand
        Port of Discharge: Nhava Sheva, India
        Equipment: 1 × 40HC
        Commodity: General cargo / non-hazardous goods
        Cargo Ready Date: 20 October 2026
        Incoterm: FOB
        Gross Weight: Approx. 18,000 kg
        Best Regards,
        Roshan Singh
        FreightFlow
        M: +91 8774376473
        E: roshan@testShippingCompany1.com
        """;
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("mode", "FCL");
    fields.put("shipmentType", "fcl");
    fields.put("origin", "Laem Chabang, Thailand");
    fields.put("destination", "Nhava Sheva, India");
    fields.put("portOfLoading", "Laem Chabang, Thailand");
    fields.put("portOfDischarge", "Nhava Sheva, India");
    fields.put("containerType", "40HC");
    fields.put("containerQuantity", "1");
    fields.put("commodity", "General cargo / non-hazardous goods");
    fields.put("readyDate", "20 October 2026");
    fields.put("incoterm", "FOB");
    fields.put("grossWeight", "18,000");
    fields.put("weightUnit", "kg");
    fields.put("insuranceRequired", "unknown");
    fields.put("unexpectedModelField", "ignored");
    AiShipmentExtraction out =
        new AiShipmentService((instruction, input, schema) -> mapper.valueToTree(fields), mapper)
            .extract(email);
    assertEquals("OCEAN", out.mode());
    assertEquals("FCL", out.shipmentType());
    assertEquals("40HC", out.containerType());
    assertEquals(1, out.containerQuantity());
    assertEquals(LocalDate.of(2026, 10, 20), out.readyDate());
    assertEquals(new BigDecimal("18000"), out.grossWeight());
    assertEquals("FreightFlow", out.customerName());
    assertEquals("Roshan Singh", out.contactName());
    assertNull(out.insuranceRequired());
    assertTrue(out.missingFields().isEmpty());
  }

  @Test
  void absentInformationRemainsNull() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String key :
        ((List<?>) AiShipmentService.schema().get("required"))
            .stream().map(String::valueOf).toList()) fields.put(key, null);
    fields.put("missingFields", List.of());
    fields.put("warnings", List.of());
    AiShipmentService service =
        new AiShipmentService((instruction, input, schema) -> mapper.valueToTree(fields), mapper);
    AiShipmentExtraction out = service.extract("Unspecific shipment note");
    assertNull(out.mode());
    assertNull(out.pickupRequired());
    assertNull(out.insuranceRequired());
    assertEquals(List.of("Origin", "Destination", "Commodity"), out.missingFields());
  }

  @Test
  void detectsModeSpecificMissingFieldsWithoutMakingOptionalFieldsRequired() {
    AiShipmentExtraction air =
        new AiShipmentExtraction(
            null,
            null,
            null,
            "AIR",
            "Delhi",
            "Dubai",
            null,
            null,
            null,
            null,
            null,
            "Electronics",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertEquals(List.of("Gross weight"), AiShipmentService.missing(air));
  }

  private AiShipmentExtraction extractWithEmptyModel(String input) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String key :
        ((List<?>) AiShipmentService.schema().get("required"))
            .stream().map(String::valueOf).toList()) fields.put(key, null);
    fields.put("missingFields", List.of());
    fields.put("warnings", List.of());
    return new AiShipmentService(
            (instruction, payload, schema) -> {
              assertTrue(instruction.contains("customerName means the organization"));
              assertTrue(instruction.contains("Never derive customerName from an email domain"));
              return mapper.valueToTree(fields);
            },
            mapper)
        .extract(input);
  }

  @Test
  void extractsAdjacentSignatureCompanyAndContact() {
    AiShipmentExtraction out =
        extractWithEmptyModel(
            "Best Regards,\nRoshan Singh\nFreightFlow\nM: +91 8774376473\nE: roshan@example.com");
    assertEquals("FreightFlow", out.customerName());
    assertEquals("Roshan Singh", out.contactName());
    assertEquals("+91 8774376473", out.contactPhone());
  }

  @Test
  void customerIntakeUsesFocusedSchemaAndParsesFormattedSignature() {
    String email =
        "Subject: Freight Enquiry – Laem Chabang to Nhava Sheva\n"
            + "Sender: roshan.singh089089@gmail.com\n"
            + "Dear Ajit ji,\nShipment from Laem Chabang to Nhava Sheva.\n"
            + "*Best Regards,*\nRoshan Singh\nFreightFlow\n"
            + "M: +91 8774376473\nE: roshan@testShippingCompany1.com <roshan@yourcompany.com>";
    AiShipmentService service =
        new AiShipmentService(
            (instruction, input, schema) -> {
              assertTrue(instruction.contains("customer identity"));
              assertFalse(((Map<?, ?>) schema.get("properties")).containsKey("readyDate"));
              assertTrue(input.contains("*Best Regards,*"));
              return mapper.createObjectNode().put("mode", "SPACESHIP");
            },
            mapper);
    AiCustomerExtraction out = service.extractCustomer(email);
    assertEquals("FreightFlow", out.customerName());
    assertEquals("Roshan Singh", out.contactName());
    assertEquals("+91 8774376473", out.contactPhone());
    assertEquals("roshan@testShippingCompany1.com", out.contactEmail());
    assertNull(out.customerCountry());
  }

  @Test
  void invalidCustomerModelResponseKeepsExplicitSignatureDetails() {
    AiShipmentService service =
        new AiShipmentService(
            (instruction, input, schema) -> {
              throw new ApiException(502, "AI_INVALID_RESPONSE", "Invalid model JSON");
            },
            mapper);
    AiCustomerExtraction out =
        service.extractCustomer(
            "*Best Regards,*\nRoshan Singh\nFreightFlow\nM: +91 8774376473\nE: roshan@example.com");
    assertEquals("FreightFlow", out.customerName());
    assertEquals("Roshan Singh", out.contactName());
    assertEquals("roshan@example.com", out.contactEmail());
    assertFalse(out.warnings().isEmpty());
  }

  @Test
  void extractsCompanyAfterPosition() {
    AiShipmentExtraction out =
        extractWithEmptyModel(
            "Ajit Kumar\nCountry Manager - Thailand\nBLUE WATER LOGISTICS\nM: +66 12345678\nE: ajitp@bwl.co.in");
    assertEquals("BLUE WATER LOGISTICS", out.customerName());
    assertEquals("Ajit Kumar", out.contactName());
    assertEquals("Country Manager - Thailand", out.contactPosition());
  }

  @Test
  void personOnlySignatureDoesNotInventCompany() {
    AiShipmentExtraction out = extractWithEmptyModel("Regards,\nRoshan Singh\nE: roshan@gmail.com");
    assertNull(out.customerName());
  }

  @Test
  void shipmentAndSignatureAreBothPreserved() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String key :
        ((List<?>) AiShipmentService.schema().get("required"))
            .stream().map(String::valueOf).toList()) fields.put(key, null);
    fields.put("origin", "Laem Chabang");
    fields.put("destination", "Nhava Sheva");
    fields.put("mode", "OCEAN");
    fields.put("missingFields", List.of());
    fields.put("warnings", List.of());
    AiShipmentExtraction out =
        new AiShipmentService((instruction, input, schema) -> mapper.valueToTree(fields), mapper)
            .extract(
                "Shipment from Laem Chabang to Nhava Sheva\nBest Regards,\nRoshan Singh\nFreightFlow");
    assertEquals("Laem Chabang", out.origin());
    assertEquals("Nhava Sheva", out.destination());
    assertEquals("FreightFlow", out.customerName());
  }

  @Test
  void senderDomainIsNotACompany() {
    assertNull(
        extractWithEmptyModel("Sender: team@email.hostinger.com\nRegards,\nRoshan Singh")
            .customerName());
    assertNull(
        extractWithEmptyModel("Sender: roshan@gmail.com\nRegards,\nRoshan Singh").customerName());
  }

  @Test
  void rejectsModelCompanyFoundOnlyInEmailDomain() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String key :
        ((List<?>) AiShipmentService.schema().get("required"))
            .stream().map(String::valueOf).toList()) fields.put(key, null);
    fields.put("customerName", "Gmail");
    fields.put("missingFields", List.of());
    fields.put("warnings", List.of());
    AiShipmentExtraction out =
        new AiShipmentService((instruction, input, schema) -> mapper.valueToTree(fields), mapper)
            .extract("Sender: roshan@gmail.com\nRegards,\nRoshan Singh\nE: roshan@gmail.com");
    assertNull(out.customerName());
  }

  @Test
  void unknownModeDoesNotDiscardOtherShipmentFields() {
    AiShipmentService service =
        new AiShipmentService(
            (instruction, input, schema) ->
                mapper.createObjectNode().put("mode", "SPACESHIP").put("commodity", "Garments"),
            mapper);
    AiShipmentExtraction out = service.extract("Shipment");
    assertNull(out.mode());
    assertEquals("Garments", out.commodity());
  }

  @Test
  void providerTimeoutUsesSafeError() throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("timeout"));
    GeminiClient client = new GeminiClient("test-key", "gemini-3.5-flash-lite", mapper, http);
    ApiException error =
        assertThrows(
            ApiException.class,
            () -> client.generate("instruction", "input", Map.of("type", "object")));
    assertEquals("AI_PROVIDER_ERROR", error.code);
    assertFalse(error.getMessage().contains("test-key"));
  }

  @Test
  void malformedProviderJsonUsesInvalidResponseError() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"broken-json\"}]}}]}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    GeminiClient client = new GeminiClient("test-key", "gemini-3.5-flash-lite", mapper, http);
    ApiException error =
        assertThrows(
            ApiException.class,
            () -> client.generate("instruction", "input", Map.of("type", "object")));
    assertEquals("AI_INVALID_RESPONSE", error.code);
  }

  @Test
  void readsJsonTextAfterNonTextPart() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true},{\"text\":\"{\\\"mode\\\":\\\"OCEAN\\\"}\"}]}}]}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    GeminiClient client = new GeminiClient("test-key", "gemini-3.5-flash-lite", mapper, http);
    assertEquals(
        "OCEAN",
        client.generate("instruction", "input", Map.of("type", "object")).path("mode").asText());
  }

  @Test
  void missingKeyDoesNotCallProvider() {
    GeminiClient client = new GeminiClient("", "gemini-3.5-flash-lite", mapper);
    ApiException error =
        assertThrows(
            ApiException.class,
            () -> client.generate("instruction", "input", Map.of("type", "object")));
    assertEquals("AI_NOT_CONFIGURED", error.code);
  }
}
