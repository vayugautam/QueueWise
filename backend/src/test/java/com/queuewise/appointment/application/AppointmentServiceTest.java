package com.queuewise.appointment.application;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.appointment.persistence.AppointmentRepository;
import com.queuewise.audit.domain.AuditAction;
import com.queuewise.audit.domain.AuditEvent;
import com.queuewise.common.error.ConflictException;
import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import com.queuewise.service.persistence.ServiceRepository;
import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import com.queuewise.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
    private final UUID organizationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID queueId = UUID.randomUUID();

    @Mock AppointmentRepository appointmentRepository;
    @Mock ServiceRepository serviceRepository;
    @Mock QueueRepository queueRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private AppointmentService appointmentService;
    private Organization organization;
    private ServiceDefinition service;
    private Queue queue;
    private User customer;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepository, serviceRepository, queueRepository,
                userRepository, eventPublisher);
        organization = org.mockito.Mockito.mock(Organization.class);
        service = org.mockito.Mockito.mock(ServiceDefinition.class);
        queue = org.mockito.Mockito.mock(Queue.class);
        customer = org.mockito.Mockito.mock(User.class);

        lenient().when(organization.getId()).thenReturn(organizationId);
        lenient().when(organization.getTimeZone()).thenReturn("UTC");
        lenient().when(service.getOrganization()).thenReturn(organization);
        lenient().when(service.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        lenient().when(service.getEstimatedDurationSeconds()).thenReturn(1800);
        lenient().when(service.getBookingHorizonDays()).thenReturn((short) 30);
        lenient().when(service.getSlotCapacity()).thenReturn((short) 1);
        lenient().when(queue.getId()).thenReturn(queueId);
        lenient().when(queue.getOrganization()).thenReturn(organization);
        lenient().when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        lenient().when(userRepository.findByIdAndOrganizationIdAndStatus(customerId, organizationId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(customer));
        lenient().when(queueRepository.findByOrganizationIdAndServiceIdAndBusinessDate(
                organizationId, serviceId, LocalDate.of(2026, 9, 18))).thenReturn(Optional.of(queue));
        lenient().when(queueRepository.findWithLockByIdAndStatus(queueId, QueueStatus.OPEN)).thenReturn(Optional.of(queue));
        lenient().when(appointmentRepository
                .existsByOrganizationIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
                        any(), any(), any(), any(), any())).thenReturn(false);
        lenient().when(appointmentRepository
                .existsByOrganizationIdAndCustomerIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
                        any(), any(), any(), any(), any(), any())).thenReturn(false);
    }

    @Test
    void givenActiveServiceAndOpenQueue_whenBookingFutureSlot_thenAppointmentAndAuditEventAreCreated() {
        // Given
        Instant start = Instant.parse("2026-09-18T14:00:00Z");

        // When
        Appointment appointment = appointmentService.book(organizationId, customerId, serviceId, start, NOW);

        // Then
        verify(appointmentRepository).save(appointment);
        assertThat(appointment.getScheduledStart()).isEqualTo(start);
        assertThat(appointment.getScheduledEnd()).isEqualTo(start.plusSeconds(1800));
        assertThat(appointment.getConfirmationCode()).startsWith("QW-");
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(AuditEvent.class,
                audit -> assertThat(audit.action()).isEqualTo(AuditAction.APPOINTMENT_CREATED));
    }

    @Test
    void givenExistingOverlappingBooking_whenBookingSameService_thenConflictIsReturned() {
        // Given
        Instant start = Instant.parse("2026-09-18T14:00:00Z");
        when(appointmentRepository
                .existsByOrganizationIdAndServiceIdAndStatusInAndScheduledStartLessThanAndScheduledEndGreaterThan(
                        any(), any(), any(), any(), any())).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> appointmentService.book(organizationId, customerId, serviceId, start, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void givenPastStart_whenBooking_thenConflictIsReturnedBeforePersistence() {
        // Given
        Instant start = NOW.minusSeconds(1);

        // When / Then
        assertThatThrownBy(() -> appointmentService.book(organizationId, customerId, serviceId, start, NOW))
                .isInstanceOf(ConflictException.class);
    }
}
