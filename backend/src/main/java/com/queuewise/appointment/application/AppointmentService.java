package com.queuewise.appointment.application;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.appointment.domain.AppointmentStatus;
import com.queuewise.appointment.persistence.AppointmentRepository;
import com.queuewise.audit.domain.AuditAction;
import com.queuewise.audit.domain.AuditEvent;
import com.queuewise.common.error.ConflictException;
import com.queuewise.common.error.ResourceNotFoundException;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import com.queuewise.service.persistence.ServiceRepository;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final List<AppointmentStatus> CAPACITY_STATUSES =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.LATE);

    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(AppointmentRepository appointmentRepository, ServiceRepository serviceRepository,
                              QueueRepository queueRepository, UserRepository userRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Appointment book(UUID organizationId, UUID customerId, UUID serviceId,
                            Instant scheduledStart, Instant now) {
        if (scheduledStart == null || !scheduledStart.isAfter(now)) {
            throw new ConflictException("Appointment must be scheduled in the future.");
        }

        ServiceDefinition service = serviceRepository.findById(serviceId)
                .filter(candidate -> candidate.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ConflictException("Service is not available for booking.");
        }
                if (scheduledStart.isAfter(now.plus(Duration.ofDays(service.getBookingHorizonDays())))) {
                        throw new ConflictException("Appointment is outside the booking horizon.");
                }

        User customer = userRepository.findByIdAndOrganizationIdAndStatus(
                        customerId, organizationId, com.queuewise.user.domain.UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));

                LocalDate businessDate;
                try {
                        businessDate = scheduledStart.atZone(ZoneId.of(service.getOrganization().getTimeZone())).toLocalDate();
                } catch (DateTimeException exception) {
                        throw new ConflictException("Organization timezone is invalid.");
                }
        Queue queue = queueRepository.findByOrganizationIdAndServiceIdAndBusinessDate(
                        organizationId, serviceId, businessDate)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found for service date."));
        Queue lockedQueue = queueRepository.findWithLockByIdAndStatus(queue.getId(), QueueStatus.OPEN)
                .orElseThrow(() -> new ConflictException("Queue is not open."));

        Instant scheduledEnd = scheduledStart.plusSeconds(service.getEstimatedDurationSeconds());
        boolean overlaps = service.getSlotCapacity() <= 1
                ? appointmentRepository
                    .existsByOrganizationIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
                            organizationId, serviceId, CAPACITY_STATUSES, scheduledEnd, scheduledStart)
                : appointmentRepository
                    .existsByOrganizationIdAndCustomerIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
                            organizationId, customerId, serviceId, CAPACITY_STATUSES, scheduledEnd, scheduledStart);
        if (overlaps) {
            throw new ConflictException("Appointment overlaps an existing booking.");
        }

                Appointment appointment = new Appointment(lockedQueue.getOrganization(), customer, service,
                lockedQueue, scheduledStart, scheduledEnd, confirmationCode());
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(AuditEvent.success(
                organizationId, customerId, AuditAction.APPOINTMENT_CREATED, "Appointment", appointment.getId(),
                Map.of("serviceId", serviceId, "scheduledStart", scheduledStart.toString())));
        return appointment;
    }

    private String confirmationCode() {
        return "QW-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}