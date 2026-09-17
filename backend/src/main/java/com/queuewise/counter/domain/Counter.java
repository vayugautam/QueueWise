package com.queuewise.counter.domain;

import com.queuewise.organization.domain.Organization;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "counters")
public class Counter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "location_label", length = 120)
    private String locationLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CounterStatus status = CounterStatus.AVAILABLE;

    @Column(name = "pause_reason", length = 255)
    private String pauseReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_staff_id")
    private User currentStaff;

    @ManyToMany
    @JoinTable(name = "counter_services",
            joinColumns = @JoinColumn(name = "counter_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<ServiceDefinition> supportedServices = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Counter() { }

    public Counter(Organization organization, String name) {
        this.organization = organization;
        this.name = name;
    }

    @PrePersist
    void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getName() { return name; }
    public CounterStatus getStatus() { return status; }
    public String getPauseReason() { return pauseReason; }
    public User getCurrentStaff() { return currentStaff; }
    public Set<ServiceDefinition> getSupportedServices() { return supportedServices; }
    public long getVersion() { return version; }

    public boolean supports(ServiceDefinition service) {
        return supportedServices.stream().anyMatch(candidate -> candidate.getId().equals(service.getId()));
    }

    public boolean isAssignedTo(User staff) {
        return currentStaff != null && currentStaff.getId().equals(staff.getId());
    }

    public void markInUse() { status = CounterStatus.IN_USE; }
    public void release() { status = CounterStatus.AVAILABLE; }

    /**
     * Pauses the counter. The counter must not be {@code INACTIVE}.
     *
     * @param reason non-null, non-blank reason required by the DB check constraint
     *               {@code ck_counters_pause_reason}
     * @throws IllegalArgumentException if reason is null or blank
     * @throws IllegalStateException    if the counter is {@code INACTIVE}
     */
    public void pause(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Pause reason is required.");
        }
        if (status == CounterStatus.INACTIVE) {
            throw new IllegalStateException("Cannot pause an inactive counter.");
        }
        this.status = CounterStatus.PAUSED;
        this.pauseReason = reason;
    }

    /**
     * Resumes a paused counter, returning it to {@code AVAILABLE}.
     *
     * @throws IllegalStateException if the counter is not currently {@code PAUSED}
     */
    public void resume() {
        if (status != CounterStatus.PAUSED) {
            throw new IllegalStateException("Counter is not paused.");
        }
        this.status = CounterStatus.AVAILABLE;
        this.pauseReason = null;
    }
}
