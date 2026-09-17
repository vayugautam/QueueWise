package com.queuewise.audit.application;

import com.queuewise.audit.domain.AuditEvent;
import com.queuewise.audit.domain.AuditLog;
import com.queuewise.audit.persistence.AuditLogRepository;
import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.persistence.OrganizationRepository;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditEventListener(AuditLogRepository auditLogRepository, 
                              OrganizationRepository organizationRepository, 
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleAuditEvent(AuditEvent event) {
        Organization org = organizationRepository.findById(event.organizationId())
                .orElseThrow(() -> new IllegalStateException("Audit organization not found."));

        User actor = null;
        if (event.actorId() != null) {
            actor = userRepository.findById(event.actorId()).orElse(null);
        }

        String metadataJson = null;
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(SafeAuditMetadata.sanitize(event.metadata()));
            } catch (Exception e) {
                metadataJson = "{}";
            }
        }

        AuditLog log = new AuditLog(
                org,
                actor,
                event.action().name(),
                event.resourceType(),
                event.resourceId(),
                event.outcome(),
                event.reason(),
                null, // beforeData
                metadataJson, // afterData
                event.correlationId(),
                event.ipAddress(),
                event.occurredAt()
        );

        auditLogRepository.save(log);
    }
}
