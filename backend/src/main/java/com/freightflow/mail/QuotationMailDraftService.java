package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entry point for the quotation module to stage an editable mail draft and its PDF. */
@Service
public class QuotationMailDraftService {
  private final OutboundMailService drafts;
  private final MailAttachmentService attachments;

  QuotationMailDraftService(OutboundMailService drafts, MailAttachmentService attachments) {
    this.drafts = drafts;
    this.attachments = attachments;
  }

  @Transactional
  public UUID create(
      UUID quotationId,
      UUID customerId,
      String filename,
      byte[] pdf,
      String to,
      String cc,
      String subject,
      String body,
      UUID replyToMessageId) {
    if (to == null || to.isBlank())
      throw new ApiException(400, "MAIL_RECIPIENT_REQUIRED", "Quotation has no recipient");
    DraftResponse seed =
        replyToMessageId == null ? null : drafts.createReply(replyToMessageId, false);
    var attachment = attachments.storeGeneratedPdf(filename, pdf);
    DraftRequest request =
        new DraftRequest(
            customerId,
            quotationId,
            List.of(new MailAddress(to, null)),
            cc == null || cc.isBlank() ? List.of() : List.of(new MailAddress(cc, null)),
            List.of(),
            seed == null ? subject : seed.subject(),
            body,
            List.of(attachment.getId()),
            null,
            null);
    DraftResponse result =
        seed == null ? drafts.create(request) : drafts.update(seed.id(), request);
    return result.id();
  }
}
