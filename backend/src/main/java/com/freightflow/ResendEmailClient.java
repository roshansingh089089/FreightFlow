package com.freightflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ResendEmailClient {
 private static final Logger logger=LoggerFactory.getLogger(ResendEmailClient.class);
 private static final URI SEND_URL=URI.create("https://api.resend.com/emails");
 private final String apiKey;
 private final String fromEmail;
 private final ObjectMapper mapper;
 private final HttpClient http;

 @Autowired ResendEmailClient(@Value("${resend.api-key:}") String apiKey,
                   @Value("${resend.from-email:}") String fromEmail,
                   ObjectMapper mapper) {
  this(apiKey,fromEmail,mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
 }

 ResendEmailClient(String apiKey,String fromEmail,ObjectMapper mapper,HttpClient http) {
  this.apiKey=apiKey;
  this.fromEmail=fromEmail;
  this.mapper=mapper;
  this.http=http;
 }

 boolean configured() {
  return apiKey!=null&&!apiKey.isBlank()&&fromEmail!=null&&!fromEmail.isBlank();
 }

 void send(String to,String cc,String subject,String body,String filename,byte[] pdf) {
  if(!configured())throw new ApiException(503,"EMAIL_NOT_CONFIGURED","Email sending is not configured. Set RESEND_API_KEY and RESEND_FROM_EMAIL, then restart the backend.");
  try {
   Map<String,Object> payload=new LinkedHashMap<>();
   payload.put("from",fromEmail);
   payload.put("to",List.of(to));
   if(cc!=null&&!cc.isBlank())payload.put("cc",List.of(cc));
   payload.put("subject",subject);
   payload.put("text",body);
   payload.put("html","<pre style=\"white-space:pre-wrap;font-family:inherit\">"+escapeHtml(body)+"</pre>");
   payload.put("attachments",List.of(Map.of("filename",filename,"content",Base64.getEncoder().encodeToString(pdf))));
   HttpRequest request=HttpRequest.newBuilder(SEND_URL)
    .header("Authorization","Bearer "+apiKey)
    .header("Content-Type","application/json")
    .timeout(Duration.ofSeconds(30))
    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
    .build();
   HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300) {
    logProviderError(response.statusCode(),response.body());
    throw new ApiException(502,"EMAIL_FAILED","Email delivery failed. Check the provider configuration and try again.");
   }
   JsonNode result=mapper.readTree(response.body());
   if(result.path("id").asText().isBlank()) {
    logger.error("Resend returned success without an email ID (HTTP {})",response.statusCode());
    throw new ApiException(502,"EMAIL_FAILED","Email delivery failed. Check the provider configuration and try again.");
   }
  }catch(ApiException e) {
   throw e;
  }catch(InterruptedException e) {
   Thread.currentThread().interrupt();
   logger.error("Resend request interrupted",e);
   throw new ApiException(502,"EMAIL_FAILED","Email delivery failed. Check the provider configuration and try again.");
  }catch(Exception e) {
   logger.error("Resend request failed: {}",e.toString());
   throw new ApiException(502,"EMAIL_FAILED","Email delivery failed. Check the provider configuration and try again.");
  }
 }

 private void logProviderError(int status,String body) {
  try {
   JsonNode error=mapper.readTree(body);
   logger.error("Resend rejected email: HTTP {} type={} message={}",status,error.path("name").asText("unknown"),error.path("message").asText("unknown"));
  }catch(Exception ignored) {
   logger.error("Resend rejected email: HTTP {} (unparseable error response)",status);
  }
 }

 private static String escapeHtml(String value) {
  return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
 }
}
