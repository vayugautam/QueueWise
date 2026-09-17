package com.queuewise.appointment.persistence;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.appointment.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findAllByOrganizationIdAndCustomerIdOrderByScheduledStartDesc(UUID organizationId, UUID customerId);
    boolean existsByOrganizationIdAndCustomerIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID organizationId, UUID customerId, UUID serviceId, List<AppointmentStatus> statuses,
            Instant scheduledEnd, Instant scheduledStart);

        boolean existsByOrganizationIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
            UUID organizationId, UUID serviceId, List<AppointmentStatus> statuses,
            Instant scheduledEnd, Instant scheduledStart);
}
