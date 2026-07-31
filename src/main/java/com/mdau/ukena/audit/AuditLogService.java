package com.mdau.ukena.audit;

import com.mdau.ukena.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** Runs in its own transaction so a logging failure can never roll back — or be
     *  rolled back by — the actual moderation action it's recording. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser actor, String action, String entityType,
                        String entityId, String entityName, String details) {
        auditLogRepository.save(AuditLogEntry.builder()
                .actorEmail(actor != null ? actor.email() : null)
                .actorRole(actor != null ? actor.role().name() : null)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntry> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntry> listForEntity(String entityType, String entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }
}
