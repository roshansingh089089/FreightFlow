package com.freightflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.net.http.*;
import static org.mockito.Mockito.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiShipmentServiceTest {
 final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
 @Test void parsesStructuredShipmentAndPreservesUnknowns() {
  Map<String,Object> fields=new LinkedHashMap<>();
  for(String key:((List<?>)AiShipmentService.schema().get("required")).stream().map(String::valueOf).toList())fields.put(key,null);
  fields.put("contactEmail","aajit53@gmail.com");fields.put("mode","ocean");fields.put("origin","Laem Chabang, Thailand");fields.put("destination","Hamburg, Germany");fields.put("shipmentType","fcl");fields.put("commodity","Garments");fields.put("grossWeight",18000);fields.put("weightUnit","kg");fields.put("containerQuantity",1);fields.put("containerType","40HC");fields.put("readyDate","2026-10-15");fields.put("pickupRequired",true);fields.put("destinationDeliveryRequired",true);fields.put("missingFields",List.of());fields.put("warnings",List.of());
  AiShipmentService service=new AiShipmentService((instruction,input,schema)->{assertTrue(instruction.contains("Unknown fields must be null"));assertTrue(input.contains("Laem Chabang"));return mapper.valueToTree(fields);},mapper);
  AiShipmentExtraction out=service.extract("Need 1 x 40HC from Laem Chabang to Hamburg");
  assertEquals("aajit53@gmail.com",out.contactEmail());assertEquals("OCEAN",out.mode());assertEquals(new BigDecimal("18000"),out.grossWeight());assertEquals(LocalDate.of(2026,10,15),out.readyDate());assertEquals(1,out.containerQuantity());assertNull(out.insuranceRequired());assertNull(out.incoterm());assertTrue(out.missingFields().isEmpty());
 }
 @Test void absentInformationRemainsNull(){Map<String,Object> fields=new LinkedHashMap<>();for(String key:((List<?>)AiShipmentService.schema().get("required")).stream().map(String::valueOf).toList())fields.put(key,null);fields.put("missingFields",List.of());fields.put("warnings",List.of());AiShipmentService service=new AiShipmentService((instruction,input,schema)->mapper.valueToTree(fields),mapper);AiShipmentExtraction out=service.extract("Unspecific shipment note");assertNull(out.mode());assertNull(out.pickupRequired());assertNull(out.insuranceRequired());assertEquals(List.of("Origin","Destination","Commodity"),out.missingFields());}
 @Test void detectsModeSpecificMissingFieldsWithoutMakingOptionalFieldsRequired(){AiShipmentExtraction air=new AiShipmentExtraction(null,null,null,"AIR","Delhi","Dubai",null,null,null,null,null,"Electronics",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);assertEquals(List.of("Gross weight"),AiShipmentService.missing(air));}
 @Test void invalidResponseIsSafe(){AiShipmentService service=new AiShipmentService((instruction,input,schema)->mapper.createObjectNode().put("mode","SPACESHIP"),mapper);ApiException error=assertThrows(ApiException.class,()->service.extract("Shipment"));assertEquals("AI_INVALID_RESPONSE",error.code);}
 @Test void providerTimeoutUsesSafeError() throws Exception {HttpClient http=mock(HttpClient.class);when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));GeminiClient client=new GeminiClient("test-key","gemini-3.5-flash-lite",mapper,http);ApiException error=assertThrows(ApiException.class,()->client.generate("instruction","input",Map.of("type","object")));assertEquals("AI_PROVIDER_ERROR",error.code);assertFalse(error.getMessage().contains("test-key"));}
 @Test void malformedProviderJsonUsesInvalidResponseError() throws Exception {HttpClient http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"broken-json\"}]}}]}");when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);GeminiClient client=new GeminiClient("test-key","gemini-3.5-flash-lite",mapper,http);ApiException error=assertThrows(ApiException.class,()->client.generate("instruction","input",Map.of("type","object")));assertEquals("AI_INVALID_RESPONSE",error.code);}
 @Test void missingKeyDoesNotCallProvider(){GeminiClient client=new GeminiClient("","gemini-3.5-flash-lite",mapper);ApiException error=assertThrows(ApiException.class,()->client.generate("instruction","input",Map.of("type","object")));assertEquals("AI_NOT_CONFIGURED",error.code);}
}
