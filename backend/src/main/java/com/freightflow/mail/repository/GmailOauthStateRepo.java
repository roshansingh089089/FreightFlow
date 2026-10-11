package com.freightflow.mail.repository;

import com.freightflow.mail.entity.GmailOauthState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailOauthStateRepo extends JpaRepository<GmailOauthState, UUID> {
  Optional<GmailOauthState> findByStateHash(String hash);
}
