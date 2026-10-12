package com.freightflow.quotation;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.mail.EmailSender;
import com.freightflow.mail.QuotationMailDraftService;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.entity.QuotationEmailLog;
import com.freightflow.quotation.repository.EmailLogRepo;
import com.freightflow.quotation.repository.LineRepo;
import com.freightflow.quotation.repository.QuoteRepo;
import com.freightflow.tenant.repository.TenantRepo;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotations")
class QuoteCommunicationController {
  private static final Logger logger = LoggerFactory.getLogger(QuoteCommunicationController.class);
  private static final com.fasterxml.jackson.databind.ObjectMapper SNAPSHOT_MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();
  final QuotationLookupService b;
  final LineRepo lines;
  final QuoteRepo quotes;
  final ContactRepo contacts;
  final TenantRepo tenants;
  final EmailLogRepo logs;
  final EmailSender emailClient;
  final QuotationLifecycleService lifecycle;
  final QuotationMailDraftService mailDrafts;
  final QuotationDocumentService documents;

  QuoteCommunicationController(
      QuotationLookupService b,
      LineRepo lineRepo,
      QuoteRepo quoteRepo,
      ContactRepo c,
      TenantRepo t,
      EmailLogRepo l,
      EmailSender emailClient,
      QuotationLifecycleService lifecycle,
      QuotationMailDraftService mailDrafts,
      QuotationDocumentService documents) {
    this.b = b;
    lines = lineRepo;
    quotes = quoteRepo;
    contacts = c;
    tenants = t;
    logs = l;
    this.emailClient = emailClient;
    this.lifecycle = lifecycle;
    this.mailDrafts = mailDrafts;
    this.documents = documents;
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/{id}/document")
  QuotationDocumentService.Document document(@PathVariable UUID id) {
    return documents.document(b.quote(id));
  }

  @PreAuthorize("@permissions.can('quotation.finalize')")
  @PostMapping("/{id}/finalize")
  Object finalizeQuote(@PathVariable UUID id) {
    return lifecycle.finalizeQuote(id);
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
    Quotation q = b.quote(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + q.getQuotationNumber() + ".pdf\"")
        .body(render(q));
  }

  byte[] render(Quotation q) {
    var model = documents.document(q);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document d = new Document(PageSize.A4, 38, 38, 42, 42);
    try {
      PdfWriter.getInstance(d, out);
      d.open();
      Font title = new Font(Font.HELVETICA, 19, Font.BOLD, new Color(43, 34, 47));
      Font heading = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(40, 122, 82));
      Font body = new Font(Font.HELVETICA, 9);
      Font muted = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(105, 117, 112));
      d.add(new Paragraph(value(model.companyName()), title));
      d.add(new Paragraph(value(model.companyCountry()), muted));
      d.add(new Paragraph("QUOTATION " + value(model.number()), heading));
      d.add(new Paragraph(" "));
      d.add(new Paragraph("Prepared for: " + value(model.customerName()), heading));
      if (model.contactName() != null)
        d.add(
            new Paragraph(
                "Attn: "
                    + model.contactName()
                    + (model.contactPosition() == null ? "" : ", " + model.contactPosition()),
                body));
      if (model.customerAddress() != null) d.add(new Paragraph(model.customerAddress(), body));
      if (model.contactEmail() != null) d.add(new Paragraph(model.contactEmail(), body));
      d.add(
          new Paragraph(
              "Issued: "
                  + value(model.issueDate())
                  + "    Valid until: "
                  + value(model.validUntil()),
              body));
      if (model.preparedBy() != null)
        d.add(
            new Paragraph(
                "Prepared by: "
                    + model.preparedBy()
                    + (model.preparedByEmail() == null ? "" : " · " + model.preparedByEmail()),
                body));
      if (model.customerReference() != null)
        d.add(new Paragraph("Customer reference: " + model.customerReference(), body));
      d.add(new Paragraph(" "));
      PdfPTable summary = new PdfPTable(new float[] {1, 1.8f, 1.8f, 1.5f, 1.25f});
      summary.setWidthPercentage(100);
      summary.setSpacingAfter(14);
      for (String label :
          java.util.List.of("MODE", "POL / ORIGIN", "POD / DISCHARGE", "CARGO", "INCOTERM"))
        summary.addCell(summaryCell(label, new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE)));
      for (String field :
          java.util.List.of(
              value(model.mode()),
              value(model.origin()),
              value(model.destination()),
              value(model.cargo()),
              value(model.incoterm())))
        summary.addCell(summaryCell(field, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
      d.add(summary);
      if (model.finalDestination() != null)
        d.add(new Paragraph("Final place of delivery: " + model.finalDestination(), body));
      if (model.transitTime() != null)
        d.add(new Paragraph("Transit time: " + model.transitTime(), body));
      d.add(new Paragraph(" "));
      PdfPTable table = new PdfPTable(new float[] {4, 2, 1, 2, 2});
      table.setWidthPercentage(100);
      table.setHeaderRows(1);
      table.setSpacingAfter(12);
      for (String h :
          java.util.List.of(
              "Description", "Basis", "Qty", "Original rate", "Amount " + model.currency()))
        table.addCell(chargeCell(h, heading, true));
      String group = null;
      for (var charge : model.charges()) {
        if (!charge.scope().equals(group)) {
          group = charge.scope();
          PdfPCell cell = new PdfPCell(new Phrase(group.toUpperCase(), heading));
          cell.setColspan(5);
          cell.setBackgroundColor(new Color(236, 245, 239));
          cell.setBorder(PdfPCell.NO_BORDER);
          cell.setPadding(7);
          table.addCell(cell);
        }
        table.addCell(chargeCell(value(charge.description()), body, false));
        table.addCell(chargeCell(value(charge.basis()), body, false));
        table.addCell(chargeCell(value(charge.quantity()), body, false));
        table.addCell(
            chargeCell(
                charge.originalCurrency() + " " + amount(charge.originalUnitPrice()), body, false));
        table.addCell(chargeCell(amount(charge.convertedAmount()), body, false));
      }
      if (model.charges().isEmpty()) {
        PdfPCell empty = chargeCell("No charges saved yet.", body, false);
        empty.setColspan(5);
        table.addCell(empty);
      }
      d.add(table);
      d.add(new Paragraph("TOTAL  " + model.currency() + " " + amount(model.total()), title));
      for (var charge : model.charges()) {
        if (charge.fxRate() != null
            && charge.originalCurrency() != null
            && !charge.originalCurrency().equals(model.currency()))
          d.add(
              new Paragraph(
                  "FX "
                      + charge.originalCurrency()
                      + " → "
                      + model.currency()
                      + ": "
                      + charge.fxRate()
                      + (charge.fxReferenceRate() == null
                          ? ""
                          : " · reference " + charge.fxReferenceRate())
                      + (charge.fxBufferPercent() == null
                          ? ""
                          : " · " + charge.fxBufferPercent() + "% buffer")
                      + " · "
                      + value(charge.fxSource())
                      + " · "
                      + value(charge.fxObservationDate()),
                  muted));
      }
      d.add(new Paragraph(" "));
      d.add(new Paragraph("INCLUDED", heading));
      d.add(
          new Paragraph(
              model.included().isEmpty()
                  ? "No priced services"
                  : String.join(" · ", model.included()),
              body));
      d.add(new Paragraph("NOT INCLUDED", heading));
      d.add(
          new Paragraph(
              model.excluded().isEmpty()
                  ? "No exclusions specified in saved quotation"
                  : String.join(" · ", model.excluded()),
              body));
      d.add(new Paragraph("TERMS", heading));
      d.add(new Paragraph(value(model.terms()), body));
      d.add(new Paragraph(" "));
      Paragraph footer =
          new Paragraph(value(model.companyName()) + " · " + value(model.number()), muted);
      footer.setAlignment(Element.ALIGN_RIGHT);
      d.add(footer);
      d.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new ApiException(500, "PDF_ERROR", "Could not generate quotation PDF");
    }
  }

  private static String value(Object value) {
    return value == null ? "—" : value.toString();
  }

  private static String amount(BigDecimal value) {
    return value == null ? "—" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static PdfPCell summaryCell(String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(new Color(43, 34, 47));
    cell.setBorder(PdfPCell.NO_BORDER);
    cell.setPadding(8);
    return cell;
  }

  private static PdfPCell chargeCell(String text, Font font, boolean header) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBorder(PdfPCell.BOTTOM);
    cell.setBorderColor(new Color(223, 230, 225));
    cell.setBorderWidth(0.5f);
    cell.setPadding(7);
    if (header) cell.setBackgroundColor(new Color(247, 249, 247));
    return cell;
  }

  record Attachment(String filename) {}

  record Preview(String to, String cc, String subject, String body, Attachment attachment) {}

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/{id}/email-preview")
  Preview preview(@PathVariable UUID id) {
    Quotation q = b.quote(id);
    Enquiry e = b.enquiry(q.getEnquiryId());
    Customer c = b.customer(q.getCustomerId());
    CustomerContact ct =
        contacts
            .findFirstByTenantIdAndCustomerIdAndPrimaryTrue(q.getTenantId(), c.getId())
            .orElse(null);
    String recipient =
        shipmentValue(q, "contactEmail", e.getContactEmail()) != null
                && !shipmentValue(q, "contactEmail", e.getContactEmail()).isBlank()
            ? shipmentValue(q, "contactEmail", e.getContactEmail())
            : ct == null ? null : ct.getEmail();
    if (recipient == null || recipient.isBlank())
      throw new ApiException(
          400, "NO_CONTACT", "Enquiry has no contact email and customer has no primary contact");
    String company = tenants.findById(q.getTenantId()).orElseThrow().getCompanyName();
    String body =
        "Dear "
            + (ct == null ? "Customer" : ct.getName())
            + ",\n\nThank you for your enquiry.\n\nPlease find attached our quotation for your shipment from "
            + shipmentValue(q, "origin", e.getOrigin())
            + " to "
            + shipmentValue(q, "destination", e.getDestination())
            + ".\n\nQuotation: "
            + q.getQuotationNumber()
            + "\nMode: "
            + shipmentValue(q, "mode", e.getMode())
            + "\nQuoted Amount: "
            + q.getCurrency()
            + " "
            + q.getTotalAmount()
            + "\nTransit Time: "
            + Objects.toString(q.getTransitTimeText(), "To be confirmed")
            + "\nValid Until: "
            + q.getValidUntil()
            + "\n\nPlease review the attached quotation and let us know if you would like us to proceed.\n\nRegards,\n"
            + SecurityConfig.current().getFullName()
            + "\n"
            + company;
    return new Preview(
        recipient,
        "",
        "Quotation "
            + q.getQuotationNumber()
            + " | "
            + shipmentValue(q, "origin", e.getOrigin())
            + " → "
            + shipmentValue(q, "destination", e.getDestination()),
        body,
        new Attachment(q.getQuotationNumber() + ".pdf"));
  }

  private String shipmentValue(Quotation quotation, String field, String fallback) {
    if (quotation.getShipmentSnapshot() == null) return fallback;
    try {
      var node = SNAPSHOT_MAPPER.readTree(quotation.getShipmentSnapshot()).path(field);
      return node.isMissingNode() || node.isNull() ? fallback : node.asText();
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new ApiException(
          500, "QUOTE_SNAPSHOT_INVALID", "Quotation shipment snapshot is invalid");
    }
  }

  record SendRequest(
      @Email @NotBlank String to,
      @Email String cc,
      @NotBlank String subject,
      @NotBlank String body,
      String idempotencyKey) {
    SendRequest(String to, String cc, String subject, String body) {
      this(to, cc, subject, body, null);
    }
  }

  record MailDraftRequest(
      UUID replyToMessageId, @Email String to, @Email String cc, String subject, String body) {}

  @PreAuthorize("@permissions.can('quotation.send') and @permissions.can('mail.compose')")
  @PostMapping("/{id}/mail-draft")
  Map<String, UUID> mailDraft(@PathVariable UUID id, @Valid @RequestBody MailDraftRequest request) {
    Quotation q = b.quote(id);
    if (!"READY_TO_SEND".equals(q.getStatus()) && !"SENT".equals(q.getStatus()))
      throw new ApiException(409, "QUOTE_NOT_FINALIZED", "Finalize the quotation before sending");
    Preview content = preview(id);
    UUID draftId =
        mailDrafts.create(
            q.getId(),
            q.getCustomerId(),
            q.getQuotationNumber() + ".pdf",
            render(q),
            request.to() == null || request.to().isBlank() ? content.to() : request.to(),
            request.cc() == null ? content.cc() : request.cc(),
            request.subject() == null || request.subject().isBlank()
                ? content.subject()
                : request.subject(),
            request.body() == null || request.body().isBlank() ? content.body() : request.body(),
            request.replyToMessageId());
    return Map.of("draftId", draftId);
  }

  @PreAuthorize("@permissions.can('quotation.send')")
  @PostMapping("/{id}/send-email")
  Object send(@PathVariable UUID id, @Valid @RequestBody SendRequest r) {
    Quotation q = b.quote(id);
    if (!q.getStatus().equals("READY_TO_SEND") && !q.getStatus().equals("SENT"))
      throw new ApiException(409, "QUOTE_NOT_FINALIZED", "Finalize the quotation before sending");
    if (!emailClient.configured())
      throw new ApiException(503, "EMAIL_NOT_CONFIGURED", "Mail account not configured.");
    String key =
        r.idempotencyKey() == null || r.idempotencyKey().isBlank()
            ? "quotation:" + id
            : r.idempotencyKey();
    String claimed = lifecycle.claimSend(q, key);
    if (!"CLAIMED".equals(claimed)) return Map.of("status", claimed);
    QuotationEmailLog log = new QuotationEmailLog();
    log.setTenantId(q.getTenantId());
    log.setQuotationId(q.getId());
    log.setRecipient(r.to());
    log.setCc(r.cc());
    log.setSubject(r.subject());
    log.setSentBy(SecurityConfig.current().getId());
    try {
      emailClient.send(
          r.to(), r.cc(), r.subject(), r.body(), q.getQuotationNumber() + ".pdf", render(q));
      q.setStatus("SENT");
      q.setSentAt(Instant.now());
      quotes.save(q);
      log.setStatus("SENT");
      logs.save(log);
      lifecycle.finishSend(q, true);
      return Map.of("status", "SENT", "sentAt", q.getSentAt());
    } catch (Exception ex) {
      lifecycle.finishSend(q, false);
      log.setStatus("FAILED");
      String message =
          ex instanceof ApiException
              ? ex.getMessage()
              : "Email delivery failed. Check the provider configuration and try again.";
      log.setErrorMessage(message);
      logs.save(log);
      if (ex instanceof ApiException api) throw api;
      logger.error("Quotation email send failed for quotation {}", q.getId(), ex);
      throw new ApiException(502, "EMAIL_FAILED", message);
    }
  }
}
