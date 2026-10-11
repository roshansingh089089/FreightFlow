package com.freightflow.quotation;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.enquiry.entity.Enquiry;
import com.freightflow.enquiry.repository.EnquiryRepo;
import com.freightflow.quotation.entity.Quotation;
import com.freightflow.quotation.repository.QuoteRepo;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class QuotationLookupService {
  private final QuoteRepo quotes;
  private final EnquiryRepo enquiries;
  private final CustomerRepo customers;

  QuotationLookupService(QuoteRepo quotes, EnquiryRepo enquiries, CustomerRepo customers) {
    this.quotes = quotes;
    this.enquiries = enquiries;
    this.customers = customers;
  }

  Quotation quote(UUID id) {
    return quotes
        .findByIdAndTenantId(id, SecurityConfig.current().getTenantId())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Quotation not found"));
  }

  Enquiry enquiry(UUID id) {
    return enquiries
        .findByIdAndTenantId(id, SecurityConfig.current().getTenantId())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Enquiry not found"));
  }

  Customer customer(UUID id) {
    return customers
        .findByIdAndTenantId(id, SecurityConfig.current().getTenantId())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Customer not found"));
  }
}
