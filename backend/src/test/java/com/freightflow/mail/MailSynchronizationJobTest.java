package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.exception.ApiException;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.MailConnectionRepo;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MailSynchronizationJobTest {
  @Test
  void manualAndBackgroundSyncShareOneMailboxLease() throws Exception {
    UUID connectionId = UUID.randomUUID();
    MailConnection connection = new MailConnection();
    connection.setId(connectionId);
    connection.setUserId(UUID.randomUUID());
    AppUser user = new AppUser();
    user.setId(connection.getUserId());
    AtomicInteger claims = new AtomicInteger();
    JdbcTemplate jdbc =
        mock(
            JdbcTemplate.class,
            call -> {
              if (call.getMethod().getName().equals("update")) {
                String sql = call.getArgument(0);
                return sql.contains("and active=true") ? claims.getAndIncrement() == 0 ? 1 : 0 : 1;
              }
              return RETURNS_DEFAULTS.answer(call);
            });
    MailConnectionRepo connections = mock(MailConnectionRepo.class);
    UserRepo users = mock(UserRepo.class);
    ImapMailProvider imap = mock(ImapMailProvider.class);
    when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
    when(users.findById(connection.getUserId())).thenReturn(Optional.of(user));
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(imap.sync(connection, user))
        .thenAnswer(
            call -> {
              entered.countDown();
              release.await(5, TimeUnit.SECONDS);
              return new MailSyncResult(0, 0, 0, 0);
            });
    MailSynchronizationJob job = new MailSynchronizationJob(jdbc, connections, users, imap, 1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> job.sync(connectionId));
      entered.await(5, TimeUnit.SECONDS);
      ApiException busy = assertThrows(ApiException.class, () -> job.sync(connectionId));
      assertEquals("MAIL_SYNC_BUSY", busy.code);
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      verify(imap, times(1)).sync(connection, user);
    } finally {
      release.countDown();
      job.shutdown();
    }
  }
}
