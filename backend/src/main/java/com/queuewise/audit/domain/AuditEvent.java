package com.queuewise.audit.domain;

import java.util.UUID;
import java.time.Instant;

public record AuditEvent(
    UUID organizationId,
    UUID actorId,
    AuditAction action,
    String resourceType,
    UUID resourceId,
    AuditLog.Outcome outcome,
    String reason,
    java.util.Map<String, Object> metadata,
    String correlationId,
    String ipAddress,
    Instant occurredAt
) {
    public static AuditEvent success(UUID organizationId, UUID actorId, AuditAction action, String resourceType, UUID resourceId, java.util.Map<String, Object> metadata) {
        return success(organizationId, actorId, action, resourceType, resourceId, metadata, null, null, Instant.now());
    }

    public static AuditEvent success(UUID organizationId, UUID actorId, AuditAction action,
                                     String resourceType, UUID resourceId,
                                     java.util.Map<String, Object> metadata,
                                     String correlationId, String ipAddress, Instant occurredAt) {
        return new AuditEvent(organizationId, actorId, action, resourceType, resourceId,
                AuditLog.Outcome.SUCCESS, null, metadata, correlationId, ipAddress, occurredAt);
    }
}
