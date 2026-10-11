package com.freightflow.customer.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_contacts")
@Getter
@Setter
public class CustomerContact extends BaseEntity {
  UUID customerId;
  String name;
  String email;
  String phone;
  String designation;
  String contactRole;

  @Column(name = "is_primary")
  boolean primary;

  boolean autoCc;
  boolean active = true;
}
