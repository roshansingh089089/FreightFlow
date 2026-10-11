package com.freightflow.auth.repository;

import com.freightflow.auth.entity.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByEmailIgnoreCase(String email);

  List<AppUser> findByTenantIdAndStatusOrderByFullName(UUID tenantId, String status);
}
