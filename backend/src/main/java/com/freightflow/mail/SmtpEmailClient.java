package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class SmtpEmailClient implements EmailSender {
  private static final Logger logger = LoggerFactory.getLogger(SmtpEmailClient.class);
  private final JavaMailSender appSender;
  private final String appFrom, appHost;
  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;
  private final MailTransportSupport transport;

  SmtpEmailClient(
      ObjectProvider<JavaMailSender> appSender,
      @Value("${email.smtp.from:}") String appFrom,
      @Value("${spring.mail.host:}") String appHost,
      MailConnectionRepo connections,
      MailCredentialCipher cipher,
      MailTransportSupport transport) {
    this.appSender = appSender.getIfAvailable();
    this.appFrom = appFrom;
    this.appHost = appHost;
    this.connections = connections;
    this.cipher = cipher;
    this.transport = transport;
  }

  private MailConnection active() {
    AppUser user = SecurityConfig.current();
    return connections
        .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
        .orElse(null);
  }

  public boolean configured() {
    MailConnection c = active();
    return c != null
        && ("IMAP_SMTP".equals(c.getProvider())
            || appSender != null
                && appFrom != null
                && !appFrom.isBlank()
                && appHost != null
                && !appHost.isBlank());
  }

  public void send(String to, String cc, String subject, String body, String filename, byte[] pdf) {
    MailConnection c = active();
    JavaMailSender sender = appSender;
    String from = appFrom;
    if (c == null)
      throw new ApiException(503, "EMAIL_NOT_CONFIGURED", "Mail account not configured.");
    if (c != null && "IMAP_SMTP".equals(c.getProvider())) {
      transport.validate(c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode());
      JavaMailSenderImpl selected = new JavaMailSenderImpl();
      selected.setHost(c.getSmtpHost());
      selected.setPort(c.getSmtpPort());
      selected.setUsername(c.getSmtpUsername());
      selected.setPassword(cipher.decrypt(c.getSmtpPasswordEncrypted()));
      selected.setJavaMailProperties(
          transport
              .session("smtp", c.getSmtpHost(), c.getSmtpPort(), c.getSmtpSecurityMode())
              .getProperties());
      sender = selected;
      from = c.getSmtpFrom();
    }
    if (sender == null
        || from == null
        || from.isBlank()
        || (c == null || !"IMAP_SMTP".equals(c.getProvider()))
            && (appHost == null || appHost.isBlank()))
      throw new ApiException(
          503, "EMAIL_NOT_CONFIGURED", "Email sending is not configured for this mailbox.");
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(to);
      if (cc != null && !cc.isBlank()) helper.setCc(cc);
      helper.setSubject(subject);
      helper.setText(body, false);
      helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
      sender.send(message);
    } catch (MessagingException | MailException ex) {
      logger.error("SMTP delivery failed ({})", ex.getClass().getSimpleName());
      throw new ApiException(
          502,
          "EMAIL_FAILED",
          "Email delivery failed. Check the mailbox configuration and try again.");
    }
  }
}
