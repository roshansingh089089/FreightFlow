package com.freightflow.quotation.controller;

import com.freightflow.BusinessService;
import com.freightflow.quotation.dto.request.QuoteInput;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuotationController {
  final BusinessService business;

  QuotationController(BusinessService business) {
    this.business = business;
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/enquiries/{id}/quotations")
  ResponseEntity<?> createQuote(@PathVariable UUID id, @Valid @RequestBody QuoteInput r) {
    return ResponseEntity.status(201).body(business.createQuote(id, r));
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/quotations")
  Object quotations(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return business.quotations(search, customerId, status, from, page, size);
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/quotations/{id}")
  Object getQuote(@PathVariable UUID id) {
    return business.getQuote(id);
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PutMapping("/quotations/{id}")
  Object updateQuote(@PathVariable UUID id, @Valid @RequestBody QuoteInput r) {
    return business.updateQuote(id, r);
  }
}
