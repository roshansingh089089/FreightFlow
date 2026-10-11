package com.freightflow.dashboard.controller;

import com.freightflow.BusinessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {
  final BusinessService business;

  SearchController(BusinessService business) {
    this.business = business;
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/search")
  Object search(@RequestParam String q) {
    return business.search(q);
  }
}
