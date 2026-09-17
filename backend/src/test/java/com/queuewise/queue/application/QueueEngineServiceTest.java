package com.queuewise.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queuewise.appointment.persistence.AppointmentRepository;
import com.queuewise.common.error.ConflictException;
import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import com.queuewise.counter.persistence.CounterRepository;
import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.queue.event.QueueUpdatePublisher;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenEvent;
import com.queuewise.token.domain.TokenPriority;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenEventRepository;
import com.queuewise.token.persistence.TokenRepository;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class QueueEngineServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
    private static final CallNextRequest DEFAULT_REQUEST =
            new CallNextRequest("corr-123", "127.0.0.1");

    @Mock QueueRepository queueRepository;
    @Mock CounterRepository counterRepository;
    @Mock TokenRepository tokenRepository;
    @Mock TokenEventRepository tokenEventRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock UserRepository userRepository;
    @Mock QueueUpdatePublisher queueUpdatePublisher;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks QueueEngineService service;

    // -------------------------------------------------------------------------
    // Walk-in creation
    // -------------------------------------------------------------------------

    @Test
    void walkInAllocationUsesQueueCounterAndWritesCreationEvent() {
        Organization organization = mockOrganization();
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        ServiceDefinition serviceDefinition = org.mockito.Mockito.mock(ServiceDefinition.class);
        User customer = org.mockito.Mockito.mock(User.class);
        User actor = org.mockito.Mockito.mock(User.class);
        UUID queueId = UUID.randomUUID();
        when(queueRepository.findWithLockByIdAndStatus(queueId, QueueStatus.OPEN)).thenReturn(Optional.of(queue));
        when(queue.getOrganization()).thenReturn(organization);
        when(queue.getService()).thenReturn(serviceDefinition);
        when(queue.allocateNextTokenNumber()).thenReturn(7);
        when(customer.getOrganization()).thenReturn(organization);
        when(actor.getOrganization()).thenReturn(organization);
        when(userRepository.findById(customerId())).thenReturn(Optional.of(customer));
        when(userRepository.findById(actorId())).thenReturn(Optional.of(actor));

        Token token = service.createWalkIn(queueId, customerId(), TokenPriority.NORMAL, actorId(), NOW);

        ArgumentCaptor<Token> tokenCaptor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        verify(tokenEventRepository).save(any(TokenEvent.class));
        verify(eventPublisher).publishEvent((Object) argThat(event ->
            event instanceof com.queuewise.audit.domain.AuditEvent auditEvent
                && auditEvent.action() == com.queuewise.audit.domain.AuditAction.TOKEN_CREATED));
        assertThat(token).isSameAs(tokenCaptor.getValue());
        assertThat(token.getSource()).isEqualTo(com.queuewise.token.domain.TokenSource.WALK_IN);
        assertThat(token.getArrivalAt()).isEqualTo(NOW);
    }

    // -------------------------------------------------------------------------
    // callNext — happy path
    // -------------------------------------------------------------------------

    @Test
    void callNextClaimsOnlyAnEligibleCompatibleToken() {
        Organization organization = mockOrganization();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        User staff = org.mockito.Mockito.mock(User.class);
        Token token = org.mockito.Mockito.mock(Token.class);
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        ServiceDefinition serviceDefinition = org.mockito.Mockito.mock(ServiceDefinition.class);
        UUID counterId = UUID.randomUUID();
        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(counter.supports(serviceDefinition)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(tokenRepository.existsByAssignedCounterIdAndStatusIn(any(), any())).thenReturn(false);
        when(tokenRepository.findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(any(), any()))
                .thenReturn(List.of(token));
        UUID tokenId = UUID.randomUUID();
        when(token.getId()).thenReturn(tokenId);
        when(token.getStatus()).thenReturn(TokenStatus.WAITING);
        when(token.getArrivalAt()).thenReturn(NOW.minusSeconds(60));
        when(token.getPriority()).thenReturn(TokenPriority.URGENT);
        when(token.getAssignedCounter()).thenReturn(null);
        when(token.getQueue()).thenReturn(queue);
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);
        when(token.getService()).thenReturn(serviceDefinition);
        when(serviceDefinition.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        when(token.getOrganization()).thenReturn(organization);
        when(tokenRepository.findWithLockById(tokenId)).thenReturn(Optional.of(token));

        service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW);

        verify(token).call(counter, NOW);
        verify(counter).markInUse();
        verify(tokenEventRepository).save(any(TokenEvent.class));
        verify(eventPublisher).publishEvent((Object) argThat(event ->
            event instanceof com.queuewise.audit.domain.AuditEvent auditEvent
                && auditEvent.action() == com.queuewise.audit.domain.AuditAction.TOKEN_CALLED));
    }

    // -------------------------------------------------------------------------
    // callNext — counter guard failures
    // -------------------------------------------------------------------------

    @Test
    void occupiedCounterRejectsAnotherCall() {
        UUID counterId = UUID.randomUUID();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        Organization organization = mockOrganization();
        User staff = org.mockito.Mockito.mock(User.class);
        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(tokenRepository.existsByAssignedCounterIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active token");
    }

    @Test
    void callNext_withPausedCounter_throwsWithPausedMessage() {
        UUID counterId = UUID.randomUUID();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        Organization organization = mockOrganization();
        User staff = org.mockito.Mockito.mock(User.class);
        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.PAUSED);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);

        assertThatThrownBy(() -> service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Counter is paused.");
    }

    @Test
    void callNext_withInactiveCounter_throwsWithInactiveMessage() {
        UUID counterId = UUID.randomUUID();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        Organization organization = mockOrganization();
        User staff = org.mockito.Mockito.mock(User.class);
        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.INACTIVE);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);

        assertThatThrownBy(() -> service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Counter is inactive.");
    }

    // -------------------------------------------------------------------------
    // callNext — bounded retry on concurrent token steal
    // -------------------------------------------------------------------------

    /**
     * Simulates the race where token T1 appears eligible in the candidate query but is
     * claimed by another counter between our read and our row lock.  On attempt 1 the
     * recheck fails; on attempt 2 we try again with T2 (T1 is now excluded by the new
     * query because it is no longer WAITING) and succeed.
     */
    @Test
    void callNext_tokenStolenBetweenSelectAndLock_retriesAndSucceeds() {
        Organization organization = mockOrganization();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        User staff = org.mockito.Mockito.mock(User.class);

        // T1 will be ineligible after lock (status changed externally)
        Token stolenToken = org.mockito.Mockito.mock(Token.class);
        UUID stolenId = UUID.randomUUID();

        // T2 is the next best candidate that succeeds on retry
        Token goodToken = org.mockito.Mockito.mock(Token.class);
        UUID goodId = UUID.randomUUID();

        Queue queue = org.mockito.Mockito.mock(Queue.class);
        ServiceDefinition service = org.mockito.Mockito.mock(ServiceDefinition.class);
        UUID counterId = UUID.randomUUID();

        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(counter.supports(service)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(tokenRepository.existsByAssignedCounterIdAndStatusIn(any(), any())).thenReturn(false);

        // Attempt 1: candidate list has stolenToken (appears WAITING); after lock it is ineligible.
        // Attempt 2: candidate list has only goodToken (stolenToken excluded).
        when(tokenRepository.findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(any(), any()))
                .thenReturn(List.of(stolenToken))   // attempt 1
                .thenReturn(List.of(goodToken));     // attempt 2

        // stolenToken fields — eligible before lock
        when(stolenToken.getId()).thenReturn(stolenId);
        when(stolenToken.getStatus()).thenReturn(TokenStatus.WAITING);
        when(stolenToken.getArrivalAt()).thenReturn(NOW.minusSeconds(120));
        when(stolenToken.getPriority()).thenReturn(TokenPriority.NORMAL);
        when(stolenToken.getAssignedCounter()).thenReturn(null);
        when(stolenToken.getQueue()).thenReturn(queue);
        when(stolenToken.getService()).thenReturn(service);
        when(service.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);

        // After lock, stolenToken now has an assigned counter — ineligible
        Token lockedStolenToken = org.mockito.Mockito.mock(Token.class);
        when(lockedStolenToken.getStatus()).thenReturn(TokenStatus.CALLED);   // already claimed
        when(lockedStolenToken.getService()).thenReturn(service);
        when(tokenRepository.findWithLockById(stolenId)).thenReturn(Optional.of(lockedStolenToken));

        // goodToken fields — eligible throughout
        when(goodToken.getId()).thenReturn(goodId);
        when(goodToken.getStatus()).thenReturn(TokenStatus.WAITING);
        when(goodToken.getArrivalAt()).thenReturn(NOW.minusSeconds(60));
        when(goodToken.getPriority()).thenReturn(TokenPriority.NORMAL);
        when(goodToken.getAssignedCounter()).thenReturn(null);
        when(goodToken.getQueue()).thenReturn(queue);
        when(goodToken.getService()).thenReturn(service);
        when(goodToken.getOrganization()).thenReturn(organization);
        when(tokenRepository.findWithLockById(goodId)).thenReturn(Optional.of(goodToken));

        this.service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW);

        // The stolen token must never be called; the good token must be called exactly once
        verify(lockedStolenToken, never()).call(any(), any());
        verify(goodToken, times(1)).call(counter, NOW);
    }

    /**
     * Every candidate is stolen on every attempt. After the retry budget is exhausted
     * the service throws a {@link ConflictException} rather than looping indefinitely.
     */
    @Test
    void callNext_tokenStolenOnEveryAttempt_throwsAfterMaxRetries() {
        Organization organization = mockOrganization();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        User staff = org.mockito.Mockito.mock(User.class);
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        ServiceDefinition svc = org.mockito.Mockito.mock(ServiceDefinition.class);
        UUID counterId = UUID.randomUUID();

        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(counter.supports(svc)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(tokenRepository.existsByAssignedCounterIdAndStatusIn(any(), any())).thenReturn(false);

        // Build a token that looks eligible before the lock but is ineligible after it
        Token candidateToken = org.mockito.Mockito.mock(Token.class);
        UUID candidateId = UUID.randomUUID();
        when(candidateToken.getId()).thenReturn(candidateId);
        when(candidateToken.getStatus()).thenReturn(TokenStatus.WAITING);
        when(candidateToken.getArrivalAt()).thenReturn(NOW.minusSeconds(60));
        when(candidateToken.getPriority()).thenReturn(TokenPriority.NORMAL);
        when(candidateToken.getAssignedCounter()).thenReturn(null);
        when(candidateToken.getQueue()).thenReturn(queue);
        when(candidateToken.getService()).thenReturn(svc);
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);
        when(svc.getStatus()).thenReturn(ServiceStatus.ACTIVE);

        // Always return this token from the candidate query
        when(tokenRepository.findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(any(), any()))
                .thenReturn(List.of(candidateToken));

        // After locking: always shows a different assigned counter (stolen)
        Token lockedToken = org.mockito.Mockito.mock(Token.class);
        when(lockedToken.getStatus()).thenReturn(TokenStatus.CALLED);
        when(lockedToken.getService()).thenReturn(svc);
        when(tokenRepository.findWithLockById(candidateId)).thenReturn(Optional.of(lockedToken));

        assertThatThrownBy(() -> this.service.callNext(counterId, staffId(), DEFAULT_REQUEST, NOW))
                .isInstanceOf(ConflictException.class);

        // The candidate query must have been called exactly MAX_CALL_NEXT_RETRIES times
        verify(tokenRepository, times(QueueEngineService.MAX_CALL_NEXT_RETRIES))
                .findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(any(), any());
    }

    // -------------------------------------------------------------------------
    // callNext — audit record
    // -------------------------------------------------------------------------

    @Test
    void callNext_publishesCanonicalAuditActionWithRequestContext() {
        Organization organization = mockOrganization();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        User staff = org.mockito.Mockito.mock(User.class);
        Token token = org.mockito.Mockito.mock(Token.class);
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        ServiceDefinition svc = org.mockito.Mockito.mock(ServiceDefinition.class);
        UUID counterId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(counter.getOrganization()).thenReturn(organization);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(counter.supports(svc)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(tokenRepository.existsByAssignedCounterIdAndStatusIn(any(), any())).thenReturn(false);
        when(tokenRepository.findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(any(), any()))
                .thenReturn(List.of(token));
        when(token.getId()).thenReturn(tokenId);
        when(token.getStatus()).thenReturn(TokenStatus.WAITING);
        when(token.getArrivalAt()).thenReturn(NOW.minusSeconds(30));
        when(token.getPriority()).thenReturn(TokenPriority.NORMAL);
        when(token.getAssignedCounter()).thenReturn(null);
        when(token.getQueue()).thenReturn(queue);
        when(token.getService()).thenReturn(svc);
        when(svc.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        when(token.getOrganization()).thenReturn(organization);
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);
        when(tokenRepository.findWithLockById(tokenId)).thenReturn(Optional.of(token));

        CallNextRequest req = new CallNextRequest("trace-abc-123", "10.0.0.5");
        service.callNext(counterId, staffId(), req, NOW);

        verify(eventPublisher).publishEvent((Object) argThat(event ->
            event instanceof com.queuewise.audit.domain.AuditEvent auditEvent
                && auditEvent.action() == com.queuewise.audit.domain.AuditAction.TOKEN_CALLED
                && "trace-abc-123".equals(auditEvent.correlationId())
                && "10.0.0.5".equals(auditEvent.ipAddress())));
    }

    // -------------------------------------------------------------------------
    // Completion
    // -------------------------------------------------------------------------

    @Test
    void completionReleasesCounterAndWritesTerminalEvent() {
        Organization organization = mockOrganization();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        User staff = org.mockito.Mockito.mock(User.class);
        Token token = org.mockito.Mockito.mock(Token.class);
        UUID tokenId = UUID.randomUUID();
        UUID counterId = UUID.randomUUID();
        when(tokenRepository.findWithLockById(tokenId)).thenReturn(Optional.of(token));
        when(counterRepository.findWithLockById(counterId)).thenReturn(Optional.of(counter));
        when(token.getOrganization()).thenReturn(organization);
        ServiceDefinition completionService = org.mockito.Mockito.mock(ServiceDefinition.class);
        when(completionService.getId()).thenReturn(UUID.randomUUID());
        when(token.getService()).thenReturn(completionService);
        when(token.getStatus()).thenReturn(TokenStatus.SERVING);
        when(token.getAssignedCounter()).thenReturn(counter);
        when(counter.getId()).thenReturn(counterId);
        when(counter.getStatus()).thenReturn(CounterStatus.IN_USE);
        when(counter.isAssignedTo(staff)).thenReturn(true);
        when(userRepository.findById(staffId())).thenReturn(Optional.of(staff));
        when(staff.getOrganization()).thenReturn(organization);
        when(token.getAppointment()).thenReturn(null);

        service.complete(tokenId, counterId, staffId(), NOW);

        verify(token).complete(NOW);
        verify(counter).release();
        verify(tokenEventRepository).save(any(TokenEvent.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Organization mockOrganization() {
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        UUID id = UUID.randomUUID();
        when(organization.getId()).thenReturn(id);
        return organization;
    }

    private UUID customerId() { return UUID.fromString("00000000-0000-0000-0000-000000000101"); }
    private UUID actorId()    { return UUID.fromString("00000000-0000-0000-0000-000000000102"); }
    private UUID staffId()    { return UUID.fromString("00000000-0000-0000-0000-000000000103"); }
}
