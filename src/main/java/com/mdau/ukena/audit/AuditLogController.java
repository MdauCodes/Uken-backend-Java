package com.mdau.ukena.audit;

import com.mdau.ukena.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogEntry>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditLogService.list(PageRequest.of(page, size))));
    }

    /** History for one specific product or creator — e.g. "who suspended this and when". */
    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<Page<AuditLogEntry>>> forEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditLogService.listForEntity(entityType.toUpperCase(), entityId, PageRequest.of(page, size))));
    }
}
