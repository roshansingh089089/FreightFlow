package com.freightflow.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.entity.GmailOauthState;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.GmailOauthStateRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import jakarta.mail.internet.MimeUtility;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
class GmailIntegration {
  private static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
  private final GmailOauthStateRepo states;
  private final MailConnectionRepo connections;
  private final EmailMessageRepo messages;
  private final ObjectMapper mapper;
  private final MailCredentialCipher cipher;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final SecureRandom random = new SecureRandom();
  private final String clientId, clientSecret, redirectUri;

  GmailIntegration(
      GmailOauthStateRepo states,
      MailConnectionRepo connections,
      EmailMessageRepo messages,
      ObjectMapper mapper,
      MailCredentialCipher cipher,
      @Value("${google.oauth.client-id:}") String clientId,
      @Value("${google.oauth.client-secret:}") String clientSecret,
      @Value("${google.oauth.redirect-uri:}") String redirectUri) {
    this.states = states;
    this.connections = connections;
    this.messages = messages;
    this.mapper = mapper;
    this.cipher = cipher;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  private void configured() {
    if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank())
      throw new ApiException(
          503, "GMAIL_NOT_CONFIGURED", "Set Google OAuth client ID, secret, and redirect URI.");
  }

  String connect(AppUser user) {
    configured();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    GmailOauthState pending = new GmailOauthState();
    pending.setTenantId(user.getTenantId());
    pending.setUserId(user.getId());
    pending.setStateHash(hash(state));
    pending.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
    states.save(pending);
    return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", redirectUri)
        .queryParam("response_type", "code")
        .queryParam("scope", SCOPE)
        .queryParam("access_type", "offline")
        .queryParam("prompt", "consent")
        .queryParam("state", state)
        .build()
        .encode()
        .toUriString();
  }

  @Transactional
  void callback(AppUser user, String state, String code) {
    configured();
    if (state == null || state.isBlank() || code == null || code.isBlank())
      throw new ApiException(
          400, "GMAIL_OAUTH_INVALID", "Google authorization response is incomplete.");
    GmailOauthState pending =
        states
            .findByStateHash(hash(state))
            .orElseThrow(
                () ->
                    new ApiException(
                        400,
                        "GMAIL_OAUTH_STATE",
                        "Invalid or expired Google authorization state."));
    if (pending.getExpiresAt().isBefore(Instant.now())
        || !pending.getTenantId().equals(user.getTenantId())
        || !pending.getUserId().equals(user.getId()))
      throw new ApiException(
          400, "GMAIL_OAUTH_STATE", "Invalid or expired Google authorization state.");
    states.delete(pending);
    try {
      JsonNode token =
          postForm(
              "https://oauth2.googleapis.com/token",
              Map.of(
                  "code",
                  code,
                  "client_id",
                  clientId,
                  "client_secret",
                  clientSecret,
                  "redirect_uri",
                  redirectUri,
                  "grant_type",
                  "authorization_code"));
      String access = required(token, "access_token");
      JsonNode profile = getJson("https://gmail.googleapis.com/gmail/v1/users/me/profile", access);
      String email = required(profile, "emailAddress");
      MailConnection connection =
          connections
              .findByTenantIdAndUserIdAndProvider(user.getTenantId(), user.getId(), "GMAIL_API")
              .orElseGet(MailConnection::new);
      if (connection.getEmailAddress() != null
          && !email.equalsIgnoreCase(connection.getEmailAddress()))
        throw new ApiException(
            409,
            "GMAIL_ACCOUNT_MISMATCH",
            "This user is already connected to another Gmail account.");
      String refresh = token.path("refresh_token").asText("");
      if (refresh.isBlank() && email.equalsIgnoreCase(connection.getEmailAddress()))
        refresh = readCredential(connection.getRefreshToken());
      if (refresh == null || refresh.isBlank())
        throw new ApiException(
            502,
            "GMAIL_REFRESH_MISSING",
            "Google did not provide a refresh token. Reconnect and grant consent.");
      connection.setTenantId(user.getTenantId());
      connection.setUserId(user.getId());
      connection.setEmailAddress(email);
      connection.setAccessToken(cipher.encrypt(access));
      connection.setRefreshToken(cipher.encrypt(refresh));
      connection.setTokenExpiry(
          Instant.now().plusSeconds(Math.max(60, token.path("expires_in").asLong(3600) - 60)));
      connections
          .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
          .filter(previous -> !previous.getId().equals(connection.getId()))
          .ifPresent(
              previous -> {
                previous.setActive(false);
                connections.saveAndFlush(previous);
              });
      connection.setStatus("CONNECTED");
      connection.setActive(true);
      connections.save(connection);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "GMAIL_OAUTH_FAILED", "Google connection failed. Please retry.");
    }
  }

  private MailConnection connection(AppUser user) {
    return connections
        .findByTenantIdAndUserIdAndProvider(user.getTenantId(), user.getId(), "GMAIL_API")
        .orElseThrow(() -> new ApiException(409, "GMAIL_NOT_CONNECTED", "Connect Gmail first."));
  }

  private String readCredential(String value) {
    if (value == null || value.isBlank())
      throw new ApiException(
          503, "MAIL_CREDENTIAL_UNAVAILABLE", "Mailbox credentials are unavailable.");
    return value.startsWith("v1:") ? cipher.decrypt(value) : value;
  }

  private String access(MailConnection c) {
    if (c.getTokenExpiry() != null && c.getTokenExpiry().isAfter(Instant.now().plusSeconds(30))) {
      String token = readCredential(c.getAccessToken());
      if (!c.getAccessToken().startsWith("v1:")) {
        c.setAccessToken(cipher.encrypt(token));
        c.setRefreshToken(cipher.encrypt(readCredential(c.getRefreshToken())));
        connections.save(c);
      }
      return token;
    }
    JsonNode token =
        postForm(
            "https://oauth2.googleapis.com/token",
            Map.of(
                "client_id",
                clientId,
                "client_secret",
                clientSecret,
                "refresh_token",
                readCredential(c.getRefreshToken()),
                "grant_type",
                "refresh_token"));
    String access = required(token, "access_token");
    c.setAccessToken(cipher.encrypt(access));
    c.setRefreshToken(cipher.encrypt(readCredential(c.getRefreshToken())));
    c.setTokenExpiry(
        Instant.now().plusSeconds(Math.max(60, token.path("expires_in").asLong(3600) - 60)));
    connections.save(c);
    return access;
  }

  record SyncResult(int fetched, int inserted, int skippedDuplicates, int failed) {}

  SyncResult sync(AppUser user) {
    MailConnection c = connection(user);
    String token = access(c);
    JsonNode listing =
        getJson(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages?labelIds=INBOX&maxResults=30",
            token);
    int fetched = 0, inserted = 0, skipped = 0, failed = 0;
    for (JsonNode item : listing.path("messages")) {
      fetched++;
      String id = item.path("id").asText("");
      if (id.isBlank()) {
        failed++;
        continue;
      }
      if (messages.existsByConnectionIdAndProviderMessageId(c.getId(), id)) {
        skipped++;
        continue;
      }
      try {
        JsonNode detail =
            getJson(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/"
                    + url(id)
                    + "?format=full",
                token);
        EmailMessage m = parse(detail, c);
        messages.saveAndFlush(m);
        inserted++;
      } catch (DataIntegrityViolationException duplicate) {
        skipped++;
      } catch (Exception e) {
        failed++;
      }
    }
    c.setLastSyncedAt(Instant.now());
    connections.save(c);
    return new SyncResult(fetched, inserted, skipped, failed);
  }

  private EmailMessage parse(JsonNode json, MailConnection c) {
    JsonNode payload = json.path("payload");
    Map<String, String> headers = new HashMap<>();
    for (JsonNode h : payload.path("headers"))
      headers.put(h.path("name").asText("").toLowerCase(Locale.ROOT), h.path("value").asText(""));
    String from = decodeHeader(headers.getOrDefault("from", ""));
    String name = "", email = from;
    int open = from.lastIndexOf('<'), close = from.lastIndexOf('>');
    if (open >= 0 && close > open) {
      name = from.substring(0, open).trim().replaceAll("^\"|\"$", "");
      email = from.substring(open + 1, close).trim();
    }
    EmailMessage m = new EmailMessage();
    m.setTenantId(c.getTenantId());
    m.setConnectionId(c.getId());
    m.setProviderMessageId(required(json, "id"));
    m.setProviderThreadId(json.path("threadId").asText(null));
    m.setRfcMessageId(headers.get("message-id"));
    m.setInReplyTo(headers.get("in-reply-to"));
    m.setReferenceHeaders(headers.get("references"));
    m.setFromName(name);
    m.setFromEmail(email);
    m.setToEmail(decodeHeader(headers.getOrDefault("to", "")));
    m.setSubject(decodeHeader(headers.getOrDefault("subject", "")));
    m.setBodyText(body(payload));
    m.setReceivedAt(
        Instant.ofEpochMilli(json.path("internalDate").asLong(Instant.now().toEpochMilli())));
    boolean unread = false;
    for (JsonNode label : json.path("labelIds")) if ("UNREAD".equals(label.asText())) unread = true;
    m.setRead(!unread);
    return m;
  }

  private String body(JsonNode part) {
    String plain = findPart(part, "text/plain");
    if (plain != null) return plain;
    String html = findPart(part, "text/html");
    return html == null
        ? ""
        : html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?i)<br\\s*/?>|</p>", "\n")
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim();
  }

  private String findPart(JsonNode part, String mime) {
    if (mime.equalsIgnoreCase(part.path("mimeType").asText())
        && part.path("filename").asText("").isBlank()) {
      String data = part.path("body").path("data").asText("");
      if (!data.isBlank())
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }
    for (JsonNode child : part.path("parts")) {
      String value = findPart(child, mime);
      if (value != null) return value;
    }
    return null;
  }

  record MessageList(
      UUID id,
      String fromName,
      String fromEmail,
      String subject,
      Instant receivedAt,
      boolean isRead) {}

  record MessageDetail(
      UUID id,
      String fromName,
      String fromEmail,
      String toEmail,
      String subject,
      String bodyText,
      Instant receivedAt,
      String providerThreadId,
      UUID threadId) {}

  List<MessageList> list(AppUser user) {
    MailConnection c = connection(user);
    return messages
        .findByTenantIdAndConnectionIdOrderByReceivedAtDesc(
            user.getTenantId(), c.getId(), PageRequest.of(0, 50))
        .stream()
        .map(
            m ->
                new MessageList(
                    m.getId(),
                    m.getFromName(),
                    m.getFromEmail(),
                    m.getSubject(),
                    m.getReceivedAt(),
                    m.isRead()))
        .toList();
  }

  MessageDetail detail(AppUser user, UUID id) {
    MailConnection c = connection(user);
    EmailMessage m =
        messages
            .findByIdAndTenantIdAndConnectionId(id, user.getTenantId(), c.getId())
            .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Email not found."));
    return new MessageDetail(
        m.getId(),
        m.getFromName(),
        m.getFromEmail(),
        m.getToEmail(),
        m.getSubject(),
        m.getBodyText(),
        m.getReceivedAt(),
        m.getProviderThreadId(),
        m.getThreadId());
  }

  private JsonNode postForm(String endpoint, Map<String, String> fields) {
    StringJoiner form = new StringJoiner("&");
    fields.forEach((k, v) -> form.add(url(k) + "=" + url(v)));
    return request(
        HttpRequest.newBuilder(URI.create(endpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
            .timeout(Duration.ofSeconds(20))
            .build());
  }

  private JsonNode getJson(String endpoint, String token) {
    return request(
        HttpRequest.newBuilder(URI.create(endpoint))
            .header("Authorization", "Bearer " + token)
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build());
  }

  private JsonNode request(HttpRequest request) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ApiException(
            502,
            "GMAIL_PROVIDER_ERROR",
            "Google request failed (HTTP " + response.statusCode() + ").");
      return mapper.readTree(response.body());
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(502, "GMAIL_PROVIDER_ERROR", "Google request was interrupted.");
    } catch (Exception e) {
      throw new ApiException(502, "GMAIL_PROVIDER_ERROR", "Could not contact Google.");
    }
  }

  private static String required(JsonNode node, String field) {
    String value = node.path(field).asText("");
    if (value.isBlank())
      throw new ApiException(502, "GMAIL_PROVIDER_ERROR", "Google response was incomplete.");
    return value;
  }

  private static String decodeHeader(String value) {
    try {
      return MimeUtility.decodeText(value);
    } catch (Exception ignored) {
      return value;
    }
  }

  private static String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String hash(String state) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
