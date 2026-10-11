package com.freightflow.tenant.controller;

import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record TenantUserView(UUID id, String fullName, String email, String role) {}

@RestController
@RequestMapping("/api/tenant/users")
public class TenantUserController {
  private final UserRepo users;

  TenantUserController(UserRepo users) {
    this.users = users;
  }

  @PreAuthorize("@permissions.can('customer.read')")
  @GetMapping
  List<TenantUserView> list() {
    return users
        .findByTenantIdAndStatusOrderByFullName(SecurityConfig.current().getTenantId(), "ACTIVE")
        .stream()
        .map(u -> new TenantUserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole()))
        .toList();
  }
}
