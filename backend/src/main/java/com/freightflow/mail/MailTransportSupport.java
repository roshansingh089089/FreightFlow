package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import jakarta.mail.Session;
import java.net.InetAddress;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class MailTransportSupport {
  private static final Pattern HOST =
      Pattern.compile(
          "(?i)^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
  private final boolean allowPrivate;

  MailTransportSupport(
      @Value("${mailbox.allow-private-hosts:false}") boolean allowPrivate,
      Environment environment) {
    this.allowPrivate = allowPrivate && environment.acceptsProfiles(Profiles.of("local"));
  }

  void validate(String host, Integer port, String mode) {
    if (host == null
        || (!HOST.matcher(host).matches() && !(allowPrivate && "localhost".equalsIgnoreCase(host))))
      throw new ApiException(400, "MAIL_HOST_INVALID", "Enter a valid mail server hostname.");
    if (port == null || port < 1 || port > 65535)
      throw new ApiException(
          400, "MAIL_PORT_INVALID", "Mail server port must be between 1 and 65535.");
    if (!"IMPLICIT_TLS".equals(mode) && !"STARTTLS".equals(mode))
      throw new ApiException(400, "MAIL_TLS_REQUIRED", "Choose implicit TLS or STARTTLS.");
    try {
      for (InetAddress address : InetAddress.getAllByName(host)) {
        byte[] b = address.getAddress();
        boolean uniqueLocal = b.length == 16 && (b[0] & 0xfe) == 0xfc;
        boolean carrierNat =
            b.length == 4 && (b[0] & 255) == 100 && ((b[1] & 255) >= 64) && ((b[1] & 255) <= 127);
        if (!allowPrivate
            && (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || uniqueLocal
                || carrierNat))
          throw new ApiException(
              400, "MAIL_HOST_PRIVATE", "Private or local mail hosts are not allowed.");
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          400, "MAIL_HOST_INVALID", "Mail server hostname could not be resolved.");
    }
  }

  Session session(String protocol, String host, int port, String mode) {
    Properties p = new Properties();
    String prefix = "mail." + protocol + ".";
    p.put(prefix + "host", host);
    p.put(prefix + "port", Integer.toString(port));
    p.put(prefix + "connectiontimeout", "5000");
    p.put(prefix + "timeout", "10000");
    p.put(prefix + "ssl.checkserveridentity", "true");
    if ("IMPLICIT_TLS".equals(mode)) p.put(prefix + "ssl.enable", "true");
    else {
      p.put(prefix + "starttls.enable", "true");
      p.put(prefix + "starttls.required", "true");
    }
    if ("imap".equals(protocol)) p.put(prefix + "peek", "true");
    if ("smtp".equals(protocol)) {
      p.put(prefix + "auth", "true");
      p.put(prefix + "writetimeout", "10000");
    }
    return Session.getInstance(p);
  }
}
