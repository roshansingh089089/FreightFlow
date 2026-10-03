package com.freightflow;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@MappedSuperclass @Getter @Setter abstract class BaseEntity { @Id UUID id=UUID.randomUUID(); @Column(nullable=false) UUID tenantId; @Column(nullable=false) Instant createdAt=Instant.now(); @Column(nullable=false) Instant updatedAt=Instant.now(); @PreUpdate void touch(){updatedAt=Instant.now();} }
@Entity @Table(name="tenants") @Getter @Setter class Tenant { @Id UUID id=UUID.randomUUID(); String companyName; String country; String timezone="UTC"; String defaultCurrency="USD"; String plan="FREE"; String status="ACTIVE"; Instant createdAt=Instant.now(); Instant updatedAt=Instant.now(); }
@Entity @Table(name="users") @Getter @Setter class AppUser extends BaseEntity { String fullName; String email; String passwordHash; String role="ADMIN"; String status="ACTIVE"; Instant lastLoginAt; }
@Entity @Table(name="customers") @Getter @Setter class Customer extends BaseEntity { String customerCode; String companyName; String customerType; String country; String gstin; String iec; String status="ACTIVE"; UUID salesOwnerUserId; }
@Entity @Table(name="customer_contacts") @Getter @Setter class CustomerContact extends BaseEntity { UUID customerId; String name; String email; String phone; String designation; @Column(name="is_primary") boolean primary; }
@Entity @Table(name="customer_addresses") @Getter @Setter class CustomerAddress extends BaseEntity { UUID customerId; String addressType; String line1; String line2; String city; String state; String postalCode; String country; }
@Entity @Table(name="enquiries") @Getter @Setter class Enquiry extends BaseEntity { String enquiryCode; UUID customerId; UUID contactId; String contactEmail; String mode; String origin; String destination; String portOfLoading; String portOfDischarge; String airportOfOrigin; String airportOfDestination; String commodity; BigDecimal grossWeight; String weightUnit; BigDecimal volume; String volumeUnit; Integer packageCount; LocalDate readyDate; String incoterm; String cargoType; String containerType; Integer containerQuantity; boolean pickupRequired; boolean exportCustomsRequired; boolean insuranceRequired; boolean destinationDeliveryRequired; @Column(columnDefinition="text") String notes; String status="NEW"; UUID createdBy; }
@Entity @Table(name="quotations") @Getter @Setter class Quotation extends BaseEntity { String quotationNumber; UUID enquiryId; UUID customerId; String currency; String transitTimeText; LocalDate validUntil; @Column(columnDefinition="text") String termsAndConditions; @Column(columnDefinition="text") String notes; BigDecimal totalAmount=BigDecimal.ZERO; String status="DRAFT"; UUID createdBy; Instant sentAt; @Version long version; }
@Entity @Table(name="quotation_lines") @Getter @Setter class QuotationLine { @Id UUID id=UUID.randomUUID(); UUID tenantId; UUID quotationId; String description; BigDecimal quantity; BigDecimal unitPrice; BigDecimal amount; Integer sortOrder; }
@Entity @Table(name="quotation_email_logs") @Getter @Setter class QuotationEmailLog { @Id UUID id=UUID.randomUUID(); UUID tenantId; UUID quotationId; String recipient; String cc; String subject; UUID sentBy; String status; @Column(columnDefinition="text") String errorMessage; Instant sentAt=Instant.now(); }
