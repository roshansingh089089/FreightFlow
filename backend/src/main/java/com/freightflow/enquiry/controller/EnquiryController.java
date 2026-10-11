package com.freightflow.enquiry.controller;

import com.freightflow.BusinessService;
import com.freightflow.enquiry.dto.request.EnquiryInput;
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
public class EnquiryController {
  final BusinessService business;

  EnquiryController(BusinessService business) {
    this.business = business;
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PostMapping("/enquiries")
  ResponseEntity<?> createEnquiry(@Valid @RequestBody EnquiryInput r) {
    return ResponseEntity.status(201).body(business.createEnquiry(r));
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/enquiries")
  Object enquiries(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(defaultValue = "") String mode,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return business.enquiries(search, customerId, mode, status, from, page, size);
  }

  @PreAuthorize("@permissions.can('quotation.read')")
  @GetMapping("/enquiries/{id}")
  Object getEnquiry(@PathVariable UUID id) {
    return business.getEnquiry(id);
  }

  @PreAuthorize("@permissions.can('quotation.update')")
  @PutMapping("/enquiries/{id}")
  Object updateEnquiry(@PathVariable UUID id, @Valid @RequestBody EnquiryInput r) {
    return business.updateEnquiry(id, r);
  }
}
