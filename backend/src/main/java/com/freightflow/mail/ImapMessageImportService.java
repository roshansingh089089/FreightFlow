package com.freightflow.mail;

import com.freightflow.mail.entity.EmailMessage;
import com.freightflow.mail.repository.EmailMessageRepo;
import jakarta.mail.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ImapMessageImportService {
  private final EmailMessageRepo messages;
  private final MailThreadService threads;
  private final InboundAttachmentService attachments;

  ImapMessageImportService(
      EmailMessageRepo messages, MailThreadService threads, InboundAttachmentService attachments) {
    this.messages = messages;
    this.threads = threads;
    this.attachments = attachments;
  }

  @Transactional(rollbackFor = Exception.class)
  void importMessage(EmailMessage row, Message mime) throws Exception {
    row.setThreadId(threads.resolveInbound(row).getId());
    messages.saveAndFlush(row);
    attachments.capture(mime, row);
  }
}
