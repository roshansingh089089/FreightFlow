package com.freightflow.auth.entity;

import com.freightflow.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class AppUser extends BaseEntity {
  String fullName;
  String email;
  String passwordHash;
  String role = "ADMIN";
  String status = "ACTIVE";
  Instant lastLoginAt;
}
