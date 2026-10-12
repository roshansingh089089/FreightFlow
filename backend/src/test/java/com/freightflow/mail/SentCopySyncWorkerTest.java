package com.freightflow.mail;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.mail.MessagingException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SentCopySyncWorkerTest {
  @Test
  void retriesImapFailureWithoutRepeatingSmtpAndCompletesOnce() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    SentMailboxSync synchronizer = mock(SentMailboxSync.class);
    var copy =
        new SentCopySyncWorker.Copy(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "<sent@example.test>",
            new byte[] {1, 2, 3},
            0);
    when(jdbc.query(startsWith("select sc.message_id"), any(RowMapper.class)))
        .thenReturn(List.of(copy));
    when(jdbc.update(
            startsWith("update mail_sent_copies set status='SYNCING'"),
            eq(copy.messageId()),
            eq(copy.tenantId())))
        .thenReturn(1);
    when(synchronizer.sync(copy))
        .thenThrow(new MessagingException("IMAP temporarily unavailable"))
        .thenReturn(new SentMailboxSync.Outcome("Sent", true));

    var worker = new SentCopySyncWorker(jdbc, synchronizer);
    worker.sync();
    worker.sync();

    verify(synchronizer, times(2)).sync(copy);
    verify(jdbc)
        .update(
            startsWith("update mail_sent_copies set status='RETRY',error_code"),
            eq("SENT_COPY_SYNC_FAILED"),
            anyLong(),
            eq(copy.messageId()),
            eq(copy.tenantId()));
    verify(jdbc)
        .update(
            startsWith("update mail_sent_copies set status='SYNCED'"),
            eq("Sent"),
            eq(copy.messageId()),
            eq(copy.tenantId()));
  }
}
