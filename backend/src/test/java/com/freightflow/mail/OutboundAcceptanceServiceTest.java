package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.mail.entity.OutboundMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class OutboundAcceptanceServiceTest {
  @Test
  void commitsAttemptMessageAndPrimaryQuotationInOneTransactionalMethod() throws Exception {
    assertNotNull(
        OutboundAcceptanceService.class
            .getDeclaredMethod("complete", UUID.class, OutboundMessage.class)
            .getAnnotation(Transactional.class));
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(UUID.randomUUID());
    message.setQuotationId(UUID.randomUUID());
    message.setQuotationSendKind("PRIMARY");
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    UUID attempt = UUID.randomUUID();
    new OutboundAcceptanceService(jdbc).complete(attempt, message);
    var order = inOrder(jdbc);
    order
        .verify(jdbc)
        .update(
            startsWith("update mail_send_attempts"),
            eq(attempt),
            eq(message.getTenantId()),
            eq(message.getId()));
    order
        .verify(jdbc)
        .update(
            startsWith("update outbound_messages"), eq(message.getId()), eq(message.getTenantId()));
    order
        .verify(jdbc)
        .update(
            startsWith("update quotations"),
            eq(message.getQuotationId()),
            eq(message.getTenantId()));
  }

  @Test
  void quotationPersistenceFailurePropagatesForTransactionRollbackAndReconciliation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessage message = new OutboundMessage();
    message.setTenantId(UUID.randomUUID());
    message.setQuotationId(UUID.randomUUID());
    message.setQuotationSendKind("PRIMARY");
    when(jdbc.update(startsWith("update mail_send_attempts"), any(), any(), any())).thenReturn(1);
    when(jdbc.update(startsWith("update outbound_messages"), any(), any())).thenReturn(1);
    when(jdbc.update(startsWith("update quotations"), any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThrows(
        IllegalStateException.class,
        () -> new OutboundAcceptanceService(jdbc).complete(UUID.randomUUID(), message));
  }
}
