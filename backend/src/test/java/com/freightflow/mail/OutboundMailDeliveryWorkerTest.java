package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.freightflow.mail.entity.OutboundMessage;
import com.freightflow.mail.repository.OutboundMessageRepo;
import jakarta.mail.MessagingException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OutboundMailDeliveryWorkerTest {
  private static OutboundMessage message() {
    OutboundMessage message = new OutboundMessage();
    message.setQuotationId(UUID.randomUUID());
    message.setTenantId(UUID.randomUUID());
    message.setQuotationSendKind("PRIMARY");
    message.setStatus("QUEUED");
    return message;
  }

  @Test
  void smtpFailureLeavesQuotationReadyAndMessageForReview() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundSmtpTransport transport = mock(OutboundSmtpTransport.class);
    OutboundAcceptanceService acceptance = mock(OutboundAcceptanceService.class);
    OutboundMessage message = message();
    when(jdbc.query(startsWith("select distinct m.id"), any(RowMapper.class)))
        .thenReturn(List.of());
    when(jdbc.query(startsWith("select id from outbound_messages"), any(RowMapper.class)))
        .thenReturn(List.of(message.getId()));
    when(jdbc.update(
            startsWith("update outbound_messages set status='SENDING'"), eq(message.getId())))
        .thenReturn(1, 0);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));
    doThrow(new MessagingException("SMTP unavailable")).when(transport).send(message);

    OutboundMailDeliveryWorker worker =
        new OutboundMailDeliveryWorker(jdbc, messages, transport, acceptance);
    worker.deliver();
    worker.deliver();

    verify(transport, times(1)).send(message);
    verifyNoInteractions(acceptance);
    verify(jdbc)
        .update(
            startsWith("update outbound_messages set status='NEEDS_REVIEW'"),
            eq("SMTP_OUTCOME_UNCERTAIN"),
            eq(message.getId()),
            eq(message.getTenantId()));
  }

  @Test
  void acceptedSmtpMessageCommitsLocallyOnlyOnce() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundSmtpTransport transport = mock(OutboundSmtpTransport.class);
    OutboundAcceptanceService acceptance = mock(OutboundAcceptanceService.class);
    OutboundMessage message = message();
    when(jdbc.query(startsWith("select distinct m.id"), any(RowMapper.class)))
        .thenReturn(List.of());
    when(jdbc.query(startsWith("select id from outbound_messages"), any(RowMapper.class)))
        .thenReturn(List.of(message.getId()));
    when(jdbc.update(
            startsWith("update outbound_messages set status='SENDING'"), eq(message.getId())))
        .thenReturn(1, 0);
    when(jdbc.update(
            startsWith("update mail_send_attempts set status='ACCEPTED'"),
            any(UUID.class),
            eq(message.getTenantId())))
        .thenReturn(1);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));

    OutboundMailDeliveryWorker worker =
        new OutboundMailDeliveryWorker(jdbc, messages, transport, acceptance);
    worker.deliver();
    worker.deliver();

    verify(transport, times(1)).send(message);
    verify(acceptance, times(1)).complete(any(UUID.class), eq(message));
  }

  @Test
  void acceptedMailReconcilesAfterLocalPersistenceFailureWithoutResending() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OutboundMessageRepo messages = mock(OutboundMessageRepo.class);
    OutboundSmtpTransport transport = mock(OutboundSmtpTransport.class);
    OutboundAcceptanceService acceptance = mock(OutboundAcceptanceService.class);
    OutboundMessage message = message();
    when(jdbc.query(startsWith("select distinct m.id"), any(RowMapper.class)))
        .thenReturn(List.of(), List.of(message.getId()));
    when(jdbc.query(startsWith("select id from outbound_messages"), any(RowMapper.class)))
        .thenReturn(List.of(message.getId()), List.of());
    when(jdbc.update(
            startsWith("update outbound_messages set status='SENDING'"), eq(message.getId())))
        .thenReturn(1);
    when(jdbc.update(
            startsWith("update mail_send_attempts set status='ACCEPTED'"),
            any(UUID.class),
            eq(message.getTenantId())))
        .thenReturn(1);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));
    doThrow(new IllegalStateException("database unavailable"))
        .doNothing()
        .when(acceptance)
        .complete(any(UUID.class), eq(message));

    OutboundMailDeliveryWorker worker =
        new OutboundMailDeliveryWorker(jdbc, messages, transport, acceptance);
    worker.deliver();
    ArgumentCaptor<UUID> attempt = ArgumentCaptor.forClass(UUID.class);
    verify(jdbc)
        .update(
            startsWith("update mail_send_attempts set status='ACCEPTED'"),
            attempt.capture(),
            eq(message.getTenantId()));
    when(jdbc.query(
            startsWith("select id from mail_send_attempts"),
            any(org.springframework.jdbc.core.ResultSetExtractor.class),
            eq(message.getId()),
            eq(message.getTenantId())))
        .thenReturn(attempt.getValue());
    worker.deliver();

    verify(transport, times(1)).send(message);
    verify(acceptance, times(2)).complete(attempt.getValue(), message);
    verify(jdbc, never())
        .update(
            startsWith("update mail_send_attempts set status='UNKNOWN'"),
            any(UUID.class),
            any(UUID.class));
  }
}
