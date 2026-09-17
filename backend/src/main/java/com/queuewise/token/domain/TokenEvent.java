package com.queuewise.token.domain;

import com.queuewise.organization.domain.Organization;
import com.queuewise.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_events")
public class TokenEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "token_id", nullable = false)
    private Token token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TokenEvent() { }

    public TokenEvent(Token token, Organization organization, User actor, String eventType,
                      TokenStatus fromStatus, TokenStatus toStatus, String reason, Instant createdAt) {
        this.token = token;
        this.organization = organization;
        this.actor = actor;
        this.eventType = eventType;
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus == null ? null : toStatus.name();
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getEventType() { return eventType; }
    public TokenStatus getFromStatus() { return fromStatus == null ? null : TokenStatus.valueOf(fromStatus); }
    public TokenStatus getToStatus() { return toStatus == null ? null : TokenStatus.valueOf(toStatus); }
    public String getReason() { return reason; }
}
