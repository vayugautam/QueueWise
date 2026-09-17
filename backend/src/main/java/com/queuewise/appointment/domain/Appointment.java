package com.queuewise.appointment.domain;

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
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceDefinition service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(name = "confirmation_code", nullable = false, length = 40)
    private String confirmationCode;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Appointment() { }

    public Appointment(Organization organization, User customer, ServiceDefinition service,
                       Queue queue, Instant scheduledStart, Instant scheduledEnd,
                       String confirmationCode) {
        this.organization = organization;
        this.customer = customer;
        this.service = service;
        this.queue = queue;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.confirmationCode = confirmationCode;
    }

    @PrePersist
    void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public User getCustomer() { return customer; }
    public ServiceDefinition getService() { return service; }
    public Queue getQueue() { return queue; }
    public Instant getScheduledStart() { return scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public AppointmentStatus getStatus() { return status; }
    public String getConfirmationCode() { return confirmationCode; }
    public long getVersion() { return version; }
        public Organization getOrganization() { return organization; }

    public boolean isPresent() {
        return status == AppointmentStatus.CHECKED_IN || status == AppointmentStatus.LATE;
    }

    public void complete() { status = AppointmentStatus.COMPLETED; }
    public void cancel() { status = AppointmentStatus.CANCELLED; cancelledAt = Instant.now(); }
    public void markNoShow() { status = AppointmentStatus.NO_SHOW; }
}
