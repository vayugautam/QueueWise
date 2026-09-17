package com.queuewise.service.domain;

import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
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
@Table(name = "services")
public class ServiceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @Column(name = "estimated_duration_seconds", nullable = false)
    private int estimatedDurationSeconds;

    @Column(name = "booking_horizon_days", nullable = false)
    private short bookingHorizonDays = 30;

    @Column(name = "slot_capacity", nullable = false)
    private short slotCapacity = 1;

    @Column(name = "grace_period_minutes", nullable = false)
    private short gracePeriodMinutes = 15;

    @Column(name = "cancellation_cutoff_minutes", nullable = false)
    private int cancellationCutoffMinutes = 60;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServiceDefinition() { }

    public ServiceDefinition(Organization organization, String name, int estimatedDurationSeconds) {
        this.organization = organization;
        this.name = name;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
    }

    @PrePersist
    void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getName() { return name; }
    public ServiceStatus getStatus() { return status; }
    public int getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public short getBookingHorizonDays() { return bookingHorizonDays; }
    public short getSlotCapacity() { return slotCapacity; }
    public short getGracePeriodMinutes() { return gracePeriodMinutes; }
    public long getVersion() { return version; }
}
