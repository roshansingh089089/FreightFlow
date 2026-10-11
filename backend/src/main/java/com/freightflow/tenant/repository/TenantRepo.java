package com.freightflow.tenant.repository;

import com.freightflow.tenant.entity.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepo extends JpaRepository<Tenant, UUID> {}
