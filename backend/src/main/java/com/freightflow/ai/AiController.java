package com.freightflow.ai;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
class AiController {
  private final AiShipmentService shipments;
  private final AiAssistanceService assistance;

  AiController(AiShipmentService shipments, AiAssistanceService assistance) {
    this.shipments = shipments;
    this.assistance = assistance;
  }

  record ShipmentText(@NotBlank String text) {}

  record EnquiryId(@jakarta.validation.constraints.NotNull UUID enquiryId) {}

  record SearchText(@NotBlank String text) {}

  record MailAssistRequest(
      @NotBlank String action,
      @Size(max = 10000) String context,
      @Size(max = 10000) String currentDraft,
      @Size(max = 500) String employeeRequest,
      @Size(max = 998) String subject,
      String composeMode,
      UUID customerId,
      UUID enquiryId,
      UUID quotationId) {}

  @PreAuthorize("@permissions.can('mail.compose')")
  @PostMapping("/mail/draft")
  Object mailDraft(@Valid @RequestBody MailAssistRequest request) {
    if ("SUMMARIZE".equals(request.action()))
      return assistance.mailDraft(request.action(), request.context(), request.currentDraft());
    return assistance.composeMail(
        request.action(),
        request.context(),
        request.currentDraft(),
        request.employeeRequest(),
        request.subject(),
        request.composeMode(),
        request.customerId(),
        request.enquiryId(),
        request.quotationId());
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/shipment-extract")
  Object extract(@Valid @RequestBody ShipmentText request) {
    SecurityConfig.current();
    if (request.text().length() > 10000)
      throw new ApiException(
          413, "AI_INPUT_TOO_LARGE", "Shipment text must be 10,000 characters or less.");
    return assistance.tracked(
        "SHIPMENT_EXTRACTION",
        null,
        null,
        () -> {
          AiShipmentExtraction extracted = shipments.extract(request.text());
          return Map.of(
              "extracted",
              extracted,
              "missingFields",
              extracted.missingFields(),
              "warnings",
              extracted.warnings());
        });
  }

  @PreAuthorize("@permissions.can('customer.create')")
  @PostMapping("/customers/extract")
  Object extractCustomer(@Valid @RequestBody ShipmentText request) {
    if (request.text().length() > 10000)
      throw new ApiException(
          413, "AI_INPUT_TOO_LARGE", "Customer email must be 10,000 characters or less.");
    return assistance.tracked(
        "CUSTOMER_EXTRACTION",
        null,
        null,
        () -> {
          AiCustomerExtraction x = shipments.extractCustomer(request.text());
          Map<String, String> sources = new LinkedHashMap<>();
          sources.put("customerName", source(request.text(), x.customerName()));
          sources.put("contactName", source(request.text(), x.contactName()));
          sources.put("contactEmail", source(request.text(), x.contactEmail()));
          sources.put("contactPhone", source(request.text(), x.contactPhone()));
          sources.put("contactPosition", source(request.text(), x.contactPosition()));
          sources.put("customerAddress", source(request.text(), x.customerAddress()));
          sources.put("customerCity", source(request.text(), x.customerCity()));
          sources.put("customerCountry", source(request.text(), x.customerCountry()));
          return Map.of("extracted", x, "provenance", sources, "warnings", x.warnings());
        });
  }

  private static String source(String text, String value) {
    if (value == null || value.isBlank()) return "UNKNOWN";
    String needle = value.trim().toLowerCase(Locale.ROOT);
    String lower = text.toLowerCase(Locale.ROOT);
    int signature =
        Math.max(
            lower.lastIndexOf("regards"),
            Math.max(lower.lastIndexOf("sincerely"), lower.lastIndexOf("thank you")));
    if (signature >= 0 && lower.substring(signature).contains(needle)) return "SIGNATURE";
    for (String line : lower.split("\\R"))
      if (line.startsWith("sender:") && line.contains(needle)) return "SENDER_ADDRESS";
    return lower.contains(needle) ? "EMAIL_BODY" : "UNKNOWN";
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/draft-clarification")
  Object clarification(@Valid @RequestBody EnquiryId request) {
    return assistance.draftClarification(request.enquiryId());
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/quotations/{id}/text")
  Object quoteText(@PathVariable UUID id) {
    return assistance.quoteText(id, "text");
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/quotations/{id}/check")
  Object quoteCheck(@PathVariable UUID id) {
    return assistance.quoteCheck(id);
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/quotations/{id}/email")
  Object quoteEmail(@PathVariable UUID id) {
    return assistance.quoteText(id, "email");
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/quotations/{id}/terms")
  Object quoteTerms(@PathVariable UUID id) {
    return assistance.quoteText(id, "terms");
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @PostMapping("/customers/{id}/summary")
  Object customerSummary(@PathVariable UUID id) {
    return assistance.customerSummary(id);
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @PostMapping("/search")
  Object search(@Valid @RequestBody SearchText request) {
    if (request.text().length() > 500)
      throw new ApiException(
          413, "AI_INPUT_TOO_LARGE", "Search text must be 500 characters or less.");
    return assistance.naturalSearch(request.text());
  }
}
