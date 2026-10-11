package com.freightflow.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.repository.OutboundMessageRepo;
import jakarta.mail.MessagingException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OutboundMailDeliveryWorkerTest {
  @Test
  void smtpFailureLeavesQuotationReadyAndMessageForReview() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundSmtpTransport transport = mock(OutboundSmtpTransport.class);
    OutboundMessage message = new OutboundMessage();
    message.setQuotationId(UUID.randomUUID());
    message.setTenantId(UUID.randomUUID());
    message.setStatus("QUEUED");
    when(jdbc.query(startsWith("select id from outbound_messages"), any(RowMapper.class)))
        .thenReturn(List.of(message.getId()));
    when(jdbc.update(
            startsWith("update outbound_messages set status='SENDING'"), eq(message.getId())))
        .thenReturn(1, 0);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));
    doThrow(new MessagingException("SMTP unavailable")).when(transport).send(message);

    OutboundMailDeliveryWorker worker = new OutboundMailDeliveryWorker(jdbc, messages, transport);
    worker.deliver();
    worker.deliver();

    verify(transport, times(1)).send(message);
    verify(jdbc, never()).update(startsWith("update quotations set status='SENT'"), any(), any());
    verify(jdbc)
        .update(
            startsWith("update outbound_messages set status='NEEDS_REVIEW'"), eq(message.getId()));
  }

  @Test
  void acceptedSmtpMessageMarksQuotationSentOnlyOnce() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundSmtpTransport transport = mock(OutboundSmtpTransport.class);
    OutboundMessage message = new OutboundMessage();
    message.setQuotationId(UUID.randomUUID());
    message.setTenantId(UUID.randomUUID());
    message.setStatus("QUEUED");
    when(jdbc.query(startsWith("select id from outbound_messages"), any(RowMapper.class)))
        .thenReturn(List.of(message.getId()));
    when(jdbc.update(
            startsWith("update outbound_messages set status='SENDING'"), eq(message.getId())))
        .thenReturn(1, 0);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));

    OutboundMailDeliveryWorker worker = new OutboundMailDeliveryWorker(jdbc, messages, transport);
    worker.deliver();
    worker.deliver();

    verify(transport, times(1)).send(message);
    verify(jdbc, times(1))
        .update(
            startsWith("update quotations set status='SENT'"),
            eq(message.getQuotationId()),
            eq(message.getTenantId()));
  }
}
