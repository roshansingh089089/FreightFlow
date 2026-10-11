package com.freightflow.common.security;

import com.freightflow.auth.entity.AppUser;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component("permissions")
public class PermissionPolicy {
  private static final Map<String, Set<String>> ROLES =
      Map.of(
          "SALES",
              Set.of(
                  "customer.read",
                  "customer.create",
                  "customer.update",
                  "customer.contact.manage",
                  "customer.cc.manage",
                  "mail.read",
                  "mail.compose",
                  "mail.send",
                  "mail.manage",
                  "quotation.read",
                  "quotation.update",
                  "quotation.finalize",
                  "quotation.send"),
          "SALES_MANAGER",
              Set.of(
                  "customer.read",
                  "customer.create",
                  "customer.update",
                  "customer.archive",
                  "customer.contact.manage",
                  "customer.cc.manage",
                  "mail.read",
                  "mail.compose",
                  "mail.send",
                  "mail.manage",
                  "quotation.read",
                  "quotation.update",
                  "quotation.finalize",
                  "quotation.send"),
          "PRICING",
              Set.of(
                  "customer.read",
                  "mail.read",
                  "quotation.read",
                  "quotation.update",
                  "quotation.finalize"),
          "OPERATIONS", Set.of("customer.read", "mail.read", "quotation.read"),
          "FINANCE", Set.of("customer.read", "quotation.read"),
          "READ_ONLY", Set.of("customer.read", "quotation.read"));

  public boolean can(String permission) {
    AppUser user = SecurityConfig.current();
    return "ADMIN".equals(user.getRole())
        || ROLES.getOrDefault(user.getRole(), Set.of()).contains(permission);
  }
}
