package com.queuewise.audit.persistence;

import com.queuewise.audit.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence interface for {@link AuditLog}.
 *
 * <p>All query methods are read-only (audit logs are append-only).
 * Index coverage:
 * <ul>
 *   <li>{@code ix_audit_logs_org_time}  — supports {@code findAllByOrganizationId}</li>
 *   <li>{@code ix_audit_logs_entity}    — supports entity-scoped trail queries</li>
 * </ul>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Full organisation timeline, newest first — for the admin audit log viewer. */
    Page<AuditLog> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    /** Filter by action type (e.g. all LOGIN events for an org). */
    Page<AuditLog> findAllByOrganizationIdAndActionOrderByCreatedAtDesc(
            UUID organizationId, String action, Pageable pageable);

    /** Full audit trail for a specific resource (e.g. all events on token X). */
    Page<AuditLog> findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID organizationId, String entityType, UUID entityId, Pageable pageable);
}
