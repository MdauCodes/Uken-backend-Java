package com.mdau.ukena.order.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderAccessCodeRepository extends JpaRepository<OrderAccessCode, UUID> {
    Optional<OrderAccessCode> findFirstByDisplayIdAndEmailIgnoreCaseOrderByCreatedAtDesc(String displayId, String email);

    Optional<OrderAccessCode> findByToken(String token);
}
