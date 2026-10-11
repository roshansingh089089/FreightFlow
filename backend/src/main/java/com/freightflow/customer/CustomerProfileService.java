package com.freightflow.customer;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.audit.entity.BusinessAuditEvent;
import com.freightflow.common.audit.repository.BusinessAuditRepo;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.customer.dto.request.*;
import com.freightflow.customer.dto.response.*;
import com.freightflow.customer.entity.Customer;
import com.freightflow.customer.entity.CustomerAddress;
import com.freightflow.customer.entity.CustomerContact;
import com.freightflow.customer.entity.CustomerExtraCc;
import com.freightflow.customer.entity.CustomerMailLink;
import com.freightflow.customer.repository.AddressRepo;
import com.freightflow.customer.repository.ContactRepo;
import com.freightflow.customer.repository.CustomerExtraCcRepo;
import com.freightflow.customer.repository.CustomerMailLinkRepo;
import com.freightflow.customer.repository.CustomerRepo;
import com.freightflow.mail.entity.MailConnection;
import com.freightflow.mail.repository.EmailMessageRepo;
import com.freightflow.mail.repository.MailConnectionRepo;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService {
  private final JdbcTemplate jdbc;
  private final CustomerRepo customers;
  private final ContactRepo contacts;
  private final AddressRepo addresses;
  private final CustomerExtraCcRepo extraCc;
  private final CustomerMailLinkRepo links;
  private final EmailMessageRepo messages;
  private final MailConnectionRepo connections;
  private final BusinessAuditRepo audits;
  private final UserRepo users;

  CustomerProfileService(
      JdbcTemplate jdbc,
      CustomerRepo customers,
      ContactRepo contacts,
      AddressRepo addresses,
      CustomerExtraCcRepo extraCc,
      CustomerMailLinkRepo links,
      EmailMessageRepo messages,
      MailConnectionRepo connections,
      BusinessAuditRepo audits,
      UserRepo users) {
    this.jdbc = jdbc;
    this.customers = customers;
    this.contacts = contacts;
    this.addresses = addresses;
    this.extraCc = extraCc;
    this.links = links;
    this.messages = messages;
    this.connections = connections;
    this.audits = audits;
    this.users = users;
  }

  Customer owned(UUID id) {
    return customers
        .findByIdAndTenantId(id, SecurityConfig.current().getTenantId())
        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Customer not found"));
  }

  public void assertOwner(UUID ownerId) {
    if (ownerId == null) return;
    AppUser user =
        users
            .findById(ownerId)
            .orElseThrow(() -> new ApiException(400, "OWNER_INVALID", "Account owner not found"));
    if (!user.getTenantId().equals(SecurityConfig.current().getTenantId()))
      throw new ApiException(400, "OWNER_INVALID", "Account owner not found");
  }

  public void assertUniqueCompany(String name, UUID exclude) {
    UUID tenant = SecurityConfig.current().getTenantId();
    String key = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    jdbc.query(
        "select pg_advisory_xact_lock(hashtext(?),hashtext(?))",
        (org.springframework.jdbc.core.RowCallbackHandler) row -> {},
        tenant.toString(),
        key);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from customers where tenant_id=? and lower(regexp_replace(btrim(company_name),'[[:space:]]+',' ','g'))=? and (?::uuid is null or id<>?::uuid)",
            Integer.class,
            tenant,
            key,
            exclude,
            exclude);
    if (count != null && count > 0)
      throw new ApiException(
          409,
          "CUSTOMER_DUPLICATE",
          "A customer with this company name already exists. Review the existing record.");
  }

  public void audit(String action, UUID id, String detail) {
    AppUser user = SecurityConfig.current();
    BusinessAuditEvent event = new BusinessAuditEvent();
    event.setTenantId(user.getTenantId());
    event.setActorUserId(user.getId());
    event.setEntityType("CUSTOMER");
    event.setEntityId(id);
    event.setAction(action);
    event.setDetail(detail);
    audits.save(event);
  }

  @Transactional
  CustomerContact addContact(UUID customerId, ContactRequest r) {
    Customer customer = owned(customerId);
    if (r.primary()) clearPrimary(customerId);
    CustomerContact contact = new CustomerContact();
    contact.setTenantId(customer.getTenantId());
    contact.setCustomerId(customerId);
    apply(contact, r);
    contacts.save(contact);
    audit("CONTACT_ADDED", customerId, contact.getId().toString());
    return contact;
  }

  @Transactional
  CustomerContact updateContact(UUID customerId, UUID contactId, ContactRequest r) {
    owned(customerId);
    CustomerContact contact =
        contacts
            .findByIdAndTenantIdAndCustomerId(
                contactId, SecurityConfig.current().getTenantId(), customerId)
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Contact not found"));
    if (contact.isPrimary() && !r.primary())
      throw new ApiException(
          409, "PRIMARY_CONTACT_REQUIRED", "Choose another primary contact first");
    if (r.primary()) clearPrimary(customerId);
    apply(contact, r);
    contacts.save(contact);
    audit("CONTACT_UPDATED", customerId, contactId.toString());
    return contact;
  }

  private void clearPrimary(UUID customerId) {
    for (CustomerContact contact :
        contacts.findByTenantIdAndCustomerIdOrderByPrimaryDesc(
            SecurityConfig.current().getTenantId(), customerId))
      if (contact.isPrimary()) {
        contact.setPrimary(false);
        contacts.save(contact);
      }
  }

  private static void apply(CustomerContact c, ContactRequest r) {
    if (r.primary() && !r.active())
      throw new ApiException(400, "PRIMARY_CONTACT_INACTIVE", "The primary contact must be active");
    c.setName(r.name().trim());
    c.setEmail(r.email().trim().toLowerCase(Locale.ROOT));
    c.setPhone(r.phone().trim());
    c.setDesignation(r.designation());
    c.setContactRole(r.role());
    c.setPrimary(r.primary());
    c.setAutoCc(r.autoCc());
    c.setActive(r.active());
  }

  @Transactional
  CustomerAddress addAddress(UUID customerId, AddressRequest r) {
    Customer customer = owned(customerId);
    CustomerAddress address = new CustomerAddress();
    address.setTenantId(customer.getTenantId());
    address.setCustomerId(customerId);
    apply(address, r);
    addresses.save(address);
    audit("ADDRESS_ADDED", customerId, address.getId().toString());
    return address;
  }

  @Transactional
  CustomerAddress updateAddress(UUID customerId, UUID addressId, AddressRequest r) {
    owned(customerId);
    CustomerAddress address =
        addresses
            .findByIdAndTenantIdAndCustomerId(
                addressId, SecurityConfig.current().getTenantId(), customerId)
            .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Address not found"));
    apply(address, r);
    addresses.save(address);
    audit("ADDRESS_UPDATED", customerId, addressId.toString());
    return address;
  }

  private static void apply(CustomerAddress a, AddressRequest r) {
    a.setAddressType(r.addressType().trim());
    a.setLine1(r.line1().trim());
    a.setLine2(r.line2());
    a.setCity(r.city().trim());
    a.setState(r.state());
    a.setPostalCode(r.postalCode());
    a.setCountry(r.country().trim());
  }

  public List<CustomerExtraCc> cc(UUID id) {
    owned(id);
    return extraCc.findByTenantIdAndCustomerIdAndActiveTrue(
        SecurityConfig.current().getTenantId(), id);
  }

  @Transactional
  List<CustomerExtraCc> replaceCc(UUID id, List<ExtraCcRequest> requested) {
    owned(id);
    UUID tenant = SecurityConfig.current().getTenantId();
    Set<String> seen = new HashSet<>();
    for (ExtraCcRequest r : requested)
      if (!seen.add(r.email().trim().toLowerCase(Locale.ROOT)))
        throw new ApiException(400, "CC_DUPLICATE", "Duplicate CC address");
    for (CustomerExtraCc old : cc(id)) {
      old.setActive(false);
      extraCc.save(old);
    }
    extraCc.flush();
    for (ExtraCcRequest r : requested) {
      CustomerExtraCc row = new CustomerExtraCc();
      row.setTenantId(tenant);
      row.setCustomerId(id);
      row.setEmail(r.email().trim().toLowerCase(Locale.ROOT));
      row.setDisplayName(r.displayName());
      extraCc.save(row);
    }
    audit("CC_UPDATED", id, "count=" + requested.size());
    return cc(id);
  }

  List<CustomerMailLink> emailLinks(UUID id) {
    owned(id);
    return links.findByTenantIdAndCustomerIdOrderByLinkedAtDesc(
        SecurityConfig.current().getTenantId(), id);
  }

  @Transactional
  CustomerMailLink linkEmail(UUID customerId, UUID messageId) {
    owned(customerId);
    AppUser user = SecurityConfig.current();
    MailConnection connection =
        connections
            .findByTenantIdAndUserIdAndActiveTrue(user.getTenantId(), user.getId())
            .orElseThrow(
                () -> new ApiException(409, "MAIL_NOT_CONNECTED", "Connect a mailbox first"));
    messages
        .findByIdAndTenantIdAndConnectionId(messageId, user.getTenantId(), connection.getId())
        .orElseThrow(() -> new ApiException(404, "MAIL_NOT_FOUND", "Email not found"));
    CustomerMailLink existing =
        links.findByTenantIdAndMessageId(user.getTenantId(), messageId).orElse(null);
    if (existing != null) {
      if (existing.getCustomerId().equals(customerId)) return existing;
      throw new ApiException(409, "MAIL_ALREADY_LINKED", "Email is linked to another customer");
    }
    CustomerMailLink link = new CustomerMailLink();
    link.setTenantId(user.getTenantId());
    link.setCustomerId(customerId);
    link.setMessageId(messageId);
    link.setLinkedBy(user.getId());
    links.save(link);
    audit("MAIL_LINKED", customerId, messageId.toString());
    return link;
  }

  @Transactional
  void unlinkEmail(UUID customerId, UUID messageId) {
    owned(customerId);
    CustomerMailLink link =
        links
            .findByTenantIdAndMessageId(SecurityConfig.current().getTenantId(), messageId)
            .orElseThrow(
                () -> new ApiException(404, "MAIL_LINK_NOT_FOUND", "Email link not found"));
    if (!link.getCustomerId().equals(customerId))
      throw new ApiException(404, "MAIL_LINK_NOT_FOUND", "Email link not found");
    links.delete(link);
    audit("MAIL_UNLINKED", customerId, messageId.toString());
  }

  List<BusinessAuditEvent> activity(UUID id) {
    owned(id);
    return audits.findTop50ByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        SecurityConfig.current().getTenantId(), "CUSTOMER", id);
  }
}
