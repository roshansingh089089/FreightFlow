package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class MailSynchronizationJob {
  private final JdbcTemplate jdbc;
  private final MailConnectionRepo connections;
  private final UserRepo users;
  private final ImapMailProvider imap;
  private final int batchSize;
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "mail-sync-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  MailSynchronizationJob(
      JdbcTemplate jdbc,
      MailConnectionRepo connections,
      UserRepo users,
      ImapMailProvider imap,
      @Value("${mail.sync.accounts-per-poll:10}") int batchSize) {
    this.jdbc = jdbc;
    this.connections = connections;
    this.users = users;
    this.imap = imap;
    this.batchSize = Math.max(1, Math.min(batchSize, 100));
  }

  @Scheduled(fixedDelayString = "${mail.sync.poll-ms:60000}")
  void poll() {
    List<UUID> ids =
        jdbc.query(
            "select id from mail_connections where active=true and provider='IMAP_SMTP' "
                + "and status='CONNECTED' and (last_synced_at is null or last_synced_at < now()-interval '1 minute') "
                + "order by last_synced_at nulls first limit ?",
            (rs, row) -> (UUID) rs.getObject(1),
            batchSize);
    for (UUID id : ids) {
      try {
        sync(id);
      } catch (Exception ignored) {
        // The account's sync_error_code records the failure without disclosing credentials.
      }
    }
  }

  MailSyncResult sync(UUID id) {
    UUID leaseOwner = UUID.randomUUID();
    int claimed =
        jdbc.update(
            "update mail_connections set sync_lease_until=now()+interval '3 minutes',sync_lease_owner=? "
                + "where id=? and active=true and provider='IMAP_SMTP' and status='CONNECTED' "
                + "and (sync_lease_until is null or sync_lease_until<now())",
            leaseOwner,
            id);
    if (claimed != 1)
      throw new ApiException(409, "MAIL_SYNC_BUSY", "Mailbox synchronization is already running");
    ScheduledFuture<?> renewal =
        heartbeat.scheduleAtFixedRate(
            () -> {
              try {
                jdbc.update(
                    "update mail_connections set sync_lease_until=now()+interval '3 minutes' "
                        + "where id=? and sync_lease_owner=?",
                    id,
                    leaseOwner);
              } catch (Exception ignored) {
              }
            },
            1,
            1,
            TimeUnit.MINUTES);
    try {
      MailConnection connection = connections.findById(id).orElseThrow();
      AppUser user = users.findById(connection.getUserId()).orElseThrow();
      MailSyncResult result = imap.sync(connection, user);
      connection.setLastSyncedAt(java.time.Instant.now());
      connection.setSyncErrorCode(result.failed() == 0 ? null : "IMAP_MESSAGE_FAILED");
      connections.save(connection);
      return result;
    } catch (Exception e) {
      jdbc.update(
          "update mail_connections set sync_error_code=? where id=?",
          e instanceof ApiException api ? api.code : "IMAP_SYNC_FAILED",
          id);
      throw e;
    } finally {
      renewal.cancel(false);
      jdbc.update(
          "update mail_connections set sync_lease_until=null,sync_lease_owner=null "
              + "where id=? and sync_lease_owner=?",
          id,
          leaseOwner);
    }
  }

  @PreDestroy
  void shutdown() {
    heartbeat.shutdownNow();
  }
}
