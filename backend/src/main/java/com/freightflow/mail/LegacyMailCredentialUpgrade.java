package com.freightflow.mail;

import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LegacyMailCredentialUpgrade implements ApplicationRunner {
  private final MailConnectionRepo connections;
  private final MailCredentialCipher cipher;

  LegacyMailCredentialUpgrade(MailConnectionRepo connections, MailCredentialCipher cipher) {
    this.connections = connections;
    this.cipher = cipher;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (MailConnection connection : connections.findAll()) {
      if (!"GMAIL_API".equals(connection.getProvider())) continue;
      boolean changed = false;
      String access = connection.getAccessToken();
      if (access != null && !access.isBlank() && !access.startsWith("v1:")) {
        connection.setAccessToken(cipher.encrypt(access));
        changed = true;
      }
      String refresh = connection.getRefreshToken();
      if (refresh != null && !refresh.isBlank() && !refresh.startsWith("v1:")) {
        connection.setRefreshToken(cipher.encrypt(refresh));
        changed = true;
      }
      if (changed) connections.save(connection);
    }
  }
}
