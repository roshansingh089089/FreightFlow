package com.freightflow.customer.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
public class CustomerAddress extends BaseEntity {
  UUID customerId;
  String addressType;
  String line1;
  String line2;
  String city;
  String state;
  String postalCode;
  String country;
}
