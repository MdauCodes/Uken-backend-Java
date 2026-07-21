package com.mdau.ukena.email;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {
    Optional<Mailbox> findByAddress(String address);
    boolean existsByAddress(String address);
    List<Mailbox> findByActiveTrue();
}
