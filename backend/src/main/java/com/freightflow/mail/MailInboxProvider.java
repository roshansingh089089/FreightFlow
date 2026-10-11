package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.mail.entity.MailConnection;
import org.springframework.stereotype.Component;

record MailSyncResult(int fetched, int inserted, int skippedDuplicates, int failed) {}

interface MailInboxProvider {
  String providerType();

  MailSyncResult sync(MailConnection connection, AppUser user);
}

@Component
class GmailApiMailProvider implements MailInboxProvider {
  private final GmailIntegration gmail;

  GmailApiMailProvider(GmailIntegration gmail) {
    this.gmail = gmail;
  }

  public String providerType() {
    return "GMAIL_API";
  }

  public MailSyncResult sync(MailConnection connection, AppUser user) {
    GmailIntegration.SyncResult r = gmail.sync(user);
    return new MailSyncResult(r.fetched(), r.inserted(), r.skippedDuplicates(), r.failed());
  }
}
