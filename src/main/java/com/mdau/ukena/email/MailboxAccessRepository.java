package com.mdau.ukena.email;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MailboxAccessRepository extends JpaRepository<MailboxAccess, UUID> {
    List<MailboxAccess> findByUserId(UUID userId);
    List<MailboxAccess> findByMailboxId(UUID mailboxId);
    boolean existsByMailboxIdAndUserId(UUID mailboxId, UUID userId);
    void deleteByMailboxIdAndUserId(UUID mailboxId, UUID userId);
}
