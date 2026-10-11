package com.freightflow.customer.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_extra_cc")
@Getter
@Setter
public class CustomerExtraCc extends BaseEntity {
  UUID customerId;
  String email;
  String displayName;
  boolean active = true;
}
