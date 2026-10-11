package com.freightflow.quotation.repository;

import com.freightflow.quotation.entity.QuotationEmailLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepo extends JpaRepository<QuotationEmailLog, UUID> {}
