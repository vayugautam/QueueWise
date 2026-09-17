package com.queuewise.audit.domain;

import com.queuewise.organization.domain.Organization;
import com.queuewise.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record. Every meaningful action in the system produces one row.
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Append-only — no update operations are performed on this table.</li>
 *   <li>{@code actor_user_id} is nullable so rows survive user soft-deletion.</li>
 *   <li>{@code after_data} / {@code before_data} hold JSONB state snapshots or
 *       serialised metadata maps — rich context without schema migrations.</li>
 *   <li>Written asynchronously via {@link com.queuewise.audit.application.AuditEventListener}
 *       so audit failures never roll back the business transaction.</li>
 * </ul>
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    public enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(length = 255)
    private String reason;

    @Column(name = "before_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String afterData;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(Organization organization, User actor, String action, String entityType,
                    UUID entityId, Outcome outcome, String reason, String beforeData,
                    String afterData, String correlationId, String ipAddress, Instant createdAt) {
        this.organization = organization;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.outcome = outcome;
        this.reason = reason;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters — read-only; audit logs are immutable after creation
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Outcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getBeforeData() { return beforeData; }
    public String getAfterData() { return afterData; }
    public String getCorrelationId() { return correlationId; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }

    @PreUpdate
    void preventUpdate() {
        throw new IllegalStateException("Audit logs are append-only.");
    }

    @PreRemove
    void preventDelete() {
        throw new IllegalStateException("Audit logs are append-only.");
    }
}
