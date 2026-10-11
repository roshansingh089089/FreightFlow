package com.freightflow.customer;

import com.freightflow.customer.dto.request.*;
import com.freightflow.customer.dto.response.*;
import com.freightflow.customer.entity.CustomerAddress;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.entity.CustomerExtraCc;
import com.freightflow.customer.entity.CustomerMailLink;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{id}")
public class CustomerProfileController {
  private final CustomerProfileService service;

  CustomerProfileController(CustomerProfileService service) {
    this.service = service;
  }

  public static ContactView view(CustomerContact c) {
    return new ContactView(
        c.getId(),
        c.getName(),
        c.getEmail(),
        c.getPhone(),
        c.getDesignation(),
        c.getContactRole(),
        c.isPrimary(),
        c.isAutoCc(),
        c.isActive());
  }

  public static AddressView view(CustomerAddress a) {
    return new AddressView(
        a.getId(),
        a.getAddressType(),
        a.getLine1(),
        a.getLine2(),
        a.getCity(),
        a.getState(),
        a.getPostalCode(),
        a.getCountry());
  }

  public static ExtraCcView view(CustomerExtraCc c) {
    return new ExtraCcView(c.getId(), c.getEmail(), c.getDisplayName(), c.isActive());
  }

  public static CustomerMailLinkView view(CustomerMailLink l) {
    return new CustomerMailLinkView(l.getMessageId(), l.getLinkedAt(), l.getLinkedBy());
  }

  @PreAuthorize("@permissions.can('customer.contact.manage')")
  @PostMapping("/contacts")
  ContactView addContact(@PathVariable UUID id, @Valid @RequestBody ContactRequest r) {
    return view(service.addContact(id, r));
  }

  @PreAuthorize("@permissions.can('customer.contact.manage')")
  @PutMapping("/contacts/{contactId}")
  ContactView updateContact(
      @PathVariable UUID id, @PathVariable UUID contactId, @Valid @RequestBody ContactRequest r) {
    return view(service.updateContact(id, contactId, r));
  }

  @PreAuthorize("@permissions.can('customer.update')")
  @PostMapping("/addresses")
  AddressView addAddress(@PathVariable UUID id, @Valid @RequestBody AddressRequest r) {
    return view(service.addAddress(id, r));
  }

  @PreAuthorize("@permissions.can('customer.update')")
  @PutMapping("/addresses/{addressId}")
  AddressView updateAddress(
      @PathVariable UUID id, @PathVariable UUID addressId, @Valid @RequestBody AddressRequest r) {
    return view(service.updateAddress(id, addressId, r));
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/cc")
  List<ExtraCcView> cc(@PathVariable UUID id) {
    return service.cc(id).stream().map(CustomerProfileController::view).toList();
  }

  @PreAuthorize("@permissions.can('customer.cc.manage')")
  @PutMapping("/cc")
  List<ExtraCcView> replaceCc(
      @PathVariable UUID id, @Valid @RequestBody List<@Valid ExtraCcRequest> r) {
    return service.replaceCc(id, r).stream().map(CustomerProfileController::view).toList();
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/emails")
  List<CustomerMailLinkView> emails(@PathVariable UUID id) {
    return service.emailLinks(id).stream().map(CustomerProfileController::view).toList();
  }

  @PreAuthorize("@permissions.can('customer.update')")
  @PostMapping("/emails/{messageId}")
  CustomerMailLinkView link(@PathVariable UUID id, @PathVariable UUID messageId) {
    return view(service.linkEmail(id, messageId));
  }

  @PreAuthorize("@permissions.can('customer.update')")
  @DeleteMapping("/emails/{messageId}")
  void unlink(@PathVariable UUID id, @PathVariable UUID messageId) {
    service.unlinkEmail(id, messageId);
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping("/activity")
  List<CustomerActivityView> activity(@PathVariable UUID id) {
    return service.activity(id).stream()
        .map(
            a ->
                new CustomerActivityView(
                    a.getId(), a.getAction(), a.getDetail(), a.getCreatedAt(), a.getActorUserId()))
        .toList();
  }
}
