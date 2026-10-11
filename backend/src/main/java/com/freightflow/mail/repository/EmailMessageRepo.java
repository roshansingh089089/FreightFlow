package com.freightflow.mail.repository;

import com.freightflow.mail.entity.EmailMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailMessageRepo extends JpaRepository<EmailMessage, UUID> {
  Optional<EmailMessage> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByConnectionIdAndProviderMessageId(UUID connectionId, String providerMessageId);

  Optional<EmailMessage> findByTenantIdAndConnectionIdAndProviderMessageId(
      UUID tenantId, UUID connectionId, String providerMessageId);

  boolean existsByConnectionIdAndImapFolderAndImapUidValidityAndImapUid(
      UUID connectionId, String imapFolder, Long imapUidValidity, Long imapUid);

  List<EmailMessage> findByTenantIdAndConnectionIdAndRfcMessageId(
      UUID tenantId, UUID connectionId, String rfcMessageId);

  List<EmailMessage> findByTenantIdAndConnectionIdAndThreadIdOrderByReceivedAtAsc(
      UUID tenantId, UUID connectionId, UUID threadId);

  List<EmailMessage> findByTenantIdAndConnectionIdOrderByReceivedAtDesc(
      UUID tenantId, UUID connectionId, Pageable pageable);

  @Query(
      "select m from EmailMessage m where m.tenantId=:tenantId and m.connectionId=:connectionId "
          + "and m.imapFolder='INBOX' and m.imapUidValidity=:uidValidity "
          + "and m.imapUid is not null and m.inInbox=true "
          + "order by m.receivedAt desc")
  List<EmailMessage> findCurrentImapInbox(
      UUID tenantId, UUID connectionId, Long uidValidity, Pageable pageable);

  @Query(
      "select m from EmailMessage m where m.tenantId=:tenantId and m.connectionId=:connectionId "
          + "and m.imapFolder='INBOX' and m.imapUidValidity=:uidValidity and m.imapUid is not null")
  List<EmailMessage> findImapMessagesForReconciliation(
      UUID tenantId, UUID connectionId, Long uidValidity);

  @Query(
      "select count(m) from EmailMessage m where m.tenantId=:tenantId "
          + "and m.connectionId=:connectionId and m.imapFolder='INBOX' and m.imapUidValidity=:uidValidity "
          + "and m.imapUid is not null and m.inInbox=true")
  long countCurrentImapInbox(UUID tenantId, UUID connectionId, Long uidValidity);

  @Query(
      "select count(m) from EmailMessage m where m.tenantId=:tenantId "
          + "and m.connectionId=:connectionId and m.imapFolder='INBOX' and m.imapUidValidity=:uidValidity "
          + "and m.imapUid is not null and m.inInbox=true and m.read=false")
  long countUnreadCurrentImapInbox(UUID tenantId, UUID connectionId, Long uidValidity);

  long countByTenantIdAndConnectionId(UUID tenantId, UUID connectionId);

  long countByTenantIdAndConnectionIdAndReadFalse(UUID tenantId, UUID connectionId);

  Optional<EmailMessage> findByIdAndTenantIdAndConnectionId(
      UUID id, UUID tenantId, UUID connectionId);
}
