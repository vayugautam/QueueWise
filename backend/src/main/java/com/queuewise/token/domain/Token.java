package com.queuewise.token.domain;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.counter.domain.Counter;
import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.service.domain.ServiceDefinition;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tokens")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceDefinition service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_counter_id")
    private Counter assignedCounter;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Column(name = "display_code", nullable = false, length = 30)
    private String displayCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenPriority priority = TokenPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status = TokenStatus.WAITING;

    @Column(name = "arrival_at")
    private Instant arrivalAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "service_started_at")
    private Instant serviceStartedAt;

    @Column(name = "service_completed_at")
    private Instant serviceCompletedAt;

    @Column(name = "terminal_reason", length = 255)
    private String terminalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Token() { }

    public Token(Organization organization, Queue queue, ServiceDefinition service, User customer,
                 Appointment appointment, int tokenNumber, String displayCode, TokenSource source,
                 TokenPriority priority, Instant arrivalAt) {
        this.organization = organization;
        this.queue = queue;
        this.service = service;
        this.customer = customer;
        this.appointment = appointment;
        this.tokenNumber = tokenNumber;
        this.displayCode = displayCode;
        this.source = source;
        this.priority = priority;
        this.arrivalAt = arrivalAt;
    }

    @PrePersist
    void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Queue getQueue() { return queue; }
    public Organization getOrganization() { return organization; }
    public ServiceDefinition getService() { return service; }
    public User getCustomer() { return customer; }
    public Appointment getAppointment() { return appointment; }
    public Counter getAssignedCounter() { return assignedCounter; }
    public TokenStatus getStatus() { return status; }
    public TokenPriority getPriority() { return priority; }
    public TokenSource getSource() { return source; }
    public int getTokenNumber() { return tokenNumber; }
    public String getDisplayCode() { return displayCode; }
    public Instant getArrivalAt() { return arrivalAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getServiceStartedAt() { return serviceStartedAt; }
    public Instant getServiceCompletedAt() { return serviceCompletedAt; }
    public String getTerminalReason() { return terminalReason; }
    public long getVersion() { return version; }

    public void call(Counter counter, Instant now) {
        status = TokenStatus.CALLED;
        assignedCounter = counter;
        calledAt = now;
    }

    public void startService(Instant now) {
        status = TokenStatus.SERVING;
        serviceStartedAt = now;
    }

    public void requeue(String reason) {
        status = TokenStatus.WAITING;
        assignedCounter = null;
        terminalReason = reason;
    }

    public void cancel(String reason) { terminal(TokenStatus.CANCELLED, reason, null); }
    public void noShow(String reason) { terminal(TokenStatus.NO_SHOW, reason, null); }
    public void skip(String reason) { terminal(TokenStatus.SKIPPED, reason, null); }

    public void complete(Instant now) { terminal(TokenStatus.COMPLETED, null, now); }

    private void terminal(TokenStatus terminalStatus, String reason, Instant completedAt) {
        status = terminalStatus;
        terminalReason = reason;
        serviceCompletedAt = completedAt;
    }
}
