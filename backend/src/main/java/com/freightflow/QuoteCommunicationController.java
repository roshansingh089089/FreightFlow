package com.freightflow;
import com.lowagie.text.*;import com.lowagie.text.pdf.*;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import java.io.*;import java.time.*;import java.util.*;
@RestController @RequestMapping("/api/quotations") class QuoteCommunicationController {
 private static final Logger logger=LoggerFactory.getLogger(QuoteCommunicationController.class);
 final BusinessController b;final LineRepo lines;final QuoteRepo quotes;final ContactRepo contacts;final TenantRepo tenants;final EmailLogRepo logs;final ResendEmailClient emailClient;
 QuoteCommunicationController(BusinessController b,LineRepo lineRepo,QuoteRepo quoteRepo,ContactRepo c,TenantRepo t,EmailLogRepo l,ResendEmailClient emailClient){this.b=b;lines=lineRepo;quotes=quoteRepo;contacts=c;tenants=t;logs=l;this.emailClient=emailClient;}
 @GetMapping(value="/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE) ResponseEntity<byte[]> pdf(@PathVariable UUID id){Quotation q=b.quote(id);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+q.getQuotationNumber()+".pdf\"").body(render(q));}
 byte[] render(Quotation q){Enquiry e=b.enquiry(q.getEnquiryId());Customer c=b.customer(q.getCustomerId());Tenant t=tenants.findById(q.getTenantId()).orElseThrow();ByteArrayOutputStream out=new ByteArrayOutputStream();Document d=new Document(PageSize.A4);try{PdfWriter.getInstance(d,out);d.open();d.add(new Paragraph(t.getCompanyName(),new Font(Font.HELVETICA,20,Font.BOLD)));d.add(new Paragraph("FREIGHT QUOTATION"));d.add(new Paragraph(" "));d.add(new Paragraph("Quotation: "+q.getQuotationNumber()+"     Issued: "+LocalDate.ofInstant(q.getCreatedAt(),ZoneOffset.UTC)+"     Valid until: "+q.getValidUntil()));d.add(new Paragraph("Customer: "+c.getCompanyName()));d.add(new Paragraph("Route: "+e.getOrigin()+" to "+e.getDestination()+" | "+e.getMode()));d.add(new Paragraph("Commodity: "+e.getCommodity()));d.add(new Paragraph(" "));PdfPTable table=new PdfPTable(new float[]{5,1,2,2});table.setWidthPercentage(100);for(String h:java.util.List.of("Description","Qty","Unit price","Amount"))table.addCell(h);for(QuotationLine l:lines.findByTenantIdAndQuotationIdOrderBySortOrder(q.getTenantId(),q.getId())){table.addCell(l.getDescription());table.addCell(l.getQuantity().toPlainString());table.addCell(l.getUnitPrice().toPlainString());table.addCell(l.getAmount().toPlainString());}d.add(table);d.add(new Paragraph("Total: "+q.getCurrency()+" "+q.getTotalAmount()));d.add(new Paragraph("Transit time: "+Objects.toString(q.getTransitTimeText(),"To be confirmed")));d.add(new Paragraph("Terms and conditions: "+q.getTermsAndConditions()));d.add(new Paragraph(" "));d.add(new Paragraph("This quotation is subject to availability and applicable terms."));d.close();return out.toByteArray();}catch(Exception ex){throw new ApiException(500,"PDF_ERROR","Could not generate quotation PDF");}}
 record Attachment(String filename){} record Preview(String to,String cc,String subject,String body,Attachment attachment){}
 @GetMapping("/{id}/email-preview") Preview preview(@PathVariable UUID id){Quotation q=b.quote(id);Enquiry e=b.enquiry(q.getEnquiryId());Customer c=b.customer(q.getCustomerId());CustomerContact ct=contacts.findFirstByTenantIdAndCustomerIdAndPrimaryTrue(q.getTenantId(),c.getId()).orElse(null);String recipient=e.getContactEmail()!=null&&!e.getContactEmail().isBlank()?e.getContactEmail():ct==null?null:ct.getEmail();if(recipient==null||recipient.isBlank())throw new ApiException(400,"NO_CONTACT","Enquiry has no contact email and customer has no primary contact");String company=tenants.findById(q.getTenantId()).orElseThrow().getCompanyName();String body="Dear "+(ct==null?"Customer":ct.getName())+",\n\nThank you for your enquiry.\n\nPlease find attached our quotation for your shipment from "+e.getOrigin()+" to "+e.getDestination()+".\n\nQuotation: "+q.getQuotationNumber()+"\nMode: "+e.getMode()+"\nQuoted Amount: "+q.getCurrency()+" "+q.getTotalAmount()+"\nTransit Time: "+Objects.toString(q.getTransitTimeText(),"To be confirmed")+"\nValid Until: "+q.getValidUntil()+"\n\nPlease review the attached quotation and let us know if you would like us to proceed.\n\nRegards,\n"+SecurityConfig.current().getFullName()+"\n"+company;return new Preview(recipient,"","Quotation "+q.getQuotationNumber()+" | "+e.getOrigin()+" → "+e.getDestination(),body,new Attachment(q.getQuotationNumber()+".pdf"));}
 record SendRequest(@Email @NotBlank String to,@Email String cc,@NotBlank String subject,@NotBlank String body){}
 @PostMapping("/{id}/send-email")
 Object send(@PathVariable UUID id,@Valid @RequestBody SendRequest r){
  Quotation q=b.quote(id);
  if(!q.getStatus().equals("DRAFT")&&!q.getStatus().equals("SENT"))throw new ApiException(409,"QUOTE_STATUS","This quotation cannot be sent");
  QuotationEmailLog log=new QuotationEmailLog();
  log.setTenantId(q.getTenantId());log.setQuotationId(q.getId());log.setRecipient(r.to());log.setCc(r.cc());log.setSubject(r.subject());log.setSentBy(SecurityConfig.current().getId());
  try{
   if(!emailClient.configured())
    throw new ApiException(503,"EMAIL_NOT_CONFIGURED","Email sending is not configured. Set RESEND_API_KEY and RESEND_FROM_EMAIL, then restart the backend.");
   emailClient.send(r.to(),r.cc(),r.subject(),r.body(),q.getQuotationNumber()+".pdf",render(q));
   q.setStatus("SENT");q.setSentAt(Instant.now());quotes.save(q);
   log.setStatus("SENT");logs.save(log);
   return Map.of("status","SENT","sentAt",q.getSentAt());
  }catch(Exception ex){
   log.setStatus("FAILED");
   String message=ex instanceof ApiException ? ex.getMessage() : "Email delivery failed. Check the provider configuration and try again.";
   log.setErrorMessage(message);logs.save(log);
   if(ex instanceof ApiException api)throw api;
   logger.error("Quotation email send failed for quotation {}",q.getId(),ex);
   throw new ApiException(502,"EMAIL_FAILED",message);
  }
 }
}
