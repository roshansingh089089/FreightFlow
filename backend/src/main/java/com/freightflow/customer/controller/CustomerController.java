package com.freightflow.customer.controller;

import com.freightflow.BusinessService;
import com.freightflow.customer.dto.request.CustomerInput;
import jakarta.validation.Valid;
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
public class CustomerController {
  final BusinessService business;

  CustomerController(BusinessService business) {
    this.business = business;
  }

  @PreAuthorize("@permissions.can('customer.create')")
  @PostMapping("/customers")
  ResponseEntity<?> createCustomer(@Valid @RequestBody CustomerInput r) {
    return ResponseEntity.status(201).body(business.createCustomer(r));
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/customers")
  Object customers(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "") String country,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String direction,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return business.customers(search, type, country, status, mine, sort, direction, page, size);
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/customers/{id}")
  Object getCustomer(@PathVariable UUID id) {
    return business.getCustomer(id);
  }

  @PreAuthorize("@permissions.can('customer.update')")
  @PutMapping("/customers/{id}")
  Object updateCustomer(@PathVariable UUID id, @Valid @RequestBody CustomerInput r) {
    return business.updateCustomer(id, r);
  }

  @PreAuthorize("@permissions.can('customer.archive')")
  @PostMapping("/customers/{id}/archive")
  Object archiveCustomer(@PathVariable UUID id) {
    return business.archiveCustomer(id);
  }

  @PreAuthorize("@permissions.can('customer.archive')")
  @PostMapping("/customers/{id}/restore")
  Object restoreCustomer(@PathVariable UUID id) {
    return business.restoreCustomer(id);
  }
}
