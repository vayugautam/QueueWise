package com.queuewise.audit.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO returned by the audit log REST endpoint.
 *
 * <p><strong>Security:</strong> no passwords, JWTs, or secrets are stored in
 * {@code AuditLog} — this DTO faithfully exposes every field the entity holds.
 * The {@code ipAddress} field is surfaced only to ADMIN/SUPERVISOR callers
 * (enforced at the controller layer).
 */
public record AuditLogResponse(
        Long id,
        UUID organizationId,
        UUID actorId,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        String outcome,
        String reason,
        String beforeData,
        String afterData,
        String correlationId,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(com.queuewise.audit.domain.AuditLog log) {
        String actorName = null;
        if (log.getActor() != null) {
            actorName = log.getActor().getFirstName() + " " + log.getActor().getLastName();
        }
        return new AuditLogResponse(
                log.getId(),
                log.getOrganization() != null ? log.getOrganization().getId() : null,
                log.getActor() != null ? log.getActor().getId() : null,
                actorName,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOutcome() != null ? log.getOutcome().name() : null,
                log.getReason(),
                log.getBeforeData(),
                log.getAfterData(),
                log.getCorrelationId(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
