package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/gmail")
class GmailOauthController {
  private final GmailIntegration gmail;

  @Value("${app.frontend-origin}")
  String origin;

  GmailOauthController(GmailIntegration gmail) {
    this.gmail = gmail;
  }

  @PreAuthorize("@permissions.can('mail.manage')")
  @GetMapping("/connect")
  ResponseEntity<Void> connect() {
    return ResponseEntity.status(302)
        .header(HttpHeaders.LOCATION, gmail.connect(SecurityConfig.current()))
        .build();
  }

  @GetMapping("/callback")
  ResponseEntity<Void> callback(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String error) {
    if (error != null)
      throw new ApiException(400, "GMAIL_OAUTH_DENIED", "Google authorization was not completed.");
    gmail.callback(SecurityConfig.current(), state, code);
    return ResponseEntity.status(302)
        .header(HttpHeaders.LOCATION, origin + "/settings?gmail=connected")
        .build();
  }
}
