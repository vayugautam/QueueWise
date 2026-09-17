package com.queuewise.queue.application;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.appointment.persistence.AppointmentRepository;
import com.queuewise.audit.domain.AuditAction;
import com.queuewise.audit.domain.AuditEvent;
import com.queuewise.common.error.ConflictException;
import com.queuewise.common.error.ResourceNotFoundException;
import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import com.queuewise.counter.persistence.CounterRepository;
import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.queue.event.QueueUpdatePublisher;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenEvent;
import com.queuewise.token.domain.TokenPriority;
import com.queuewise.token.domain.TokenSource;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenEventRepository;
import com.queuewise.token.persistence.TokenRepository;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates all queue engine operations inside serialised database transactions.
 *
 * <h3>Concurrency contract for {@link #callNext}</h3>
 * <ol>
 *   <li><strong>Counter row lock</strong> ({@code SELECT … FOR UPDATE}) — acquired first for every
 *       call. Serialises same-counter requests; a second request on the same counter will observe
 *       the updated counter status after the first commits.</li>
 *   <li><strong>Token row lock</strong> ({@code SELECT … FOR UPDATE}) — acquired after candidate
 *       selection. When two different counters independently select the same highest-priority token,
 *       one blocks at this lock. After the winner commits, the loser re-reads the token, finds it
 *       ineligible, and re-runs candidate selection. A bounded retry loop (max
 *       {@value #MAX_CALL_NEXT_RETRIES} attempts) handles this transparently.</li>
 *   <li><strong>Hibernate {@code @Version}</strong> — the token's version column is a final
 *       optimistic backstop. Any update that does not match the expected version causes Hibernate to
 *       throw {@code OptimisticLockException}, which is treated as a transient conflict and counted
 *       toward the retry budget.</li>
 * </ol>
 *
 * <h3>Lock ordering — deadlock prevention</h3>
 * All paths acquire locks in the fixed order: counter row → token row. No code path acquires a
 * token lock before a counter lock, so the lock graph is acyclic and database deadlocks between
 * these two resource types are impossible.
 *
 * <h3>WebSocket publication</h3>
 * {@link QueueUpdatePublisher#publish} is called through
 * {@link TransactionSynchronizationManager#registerSynchronization} so the WebSocket broadcast
 * fires only inside the {@code afterCommit()} callback — never on a rollback.
 */
@Service
public class QueueEngineService {

    /** Maximum number of candidate-selection + token-claim attempts for {@link #callNext}. */
    static final int MAX_CALL_NEXT_RETRIES = 3;

    private static final List<TokenStatus> ACTIVE_COUNTER_STATUSES =
            List.of(TokenStatus.CALLED, TokenStatus.SERVING);

    private final QueueRepository queueRepository;
    private final CounterRepository counterRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventRepository tokenEventRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final QueueUpdatePublisher queueUpdatePublisher;
    private final ApplicationEventPublisher eventPublisher;

    public QueueEngineService(QueueRepository queueRepository, CounterRepository counterRepository,
                              TokenRepository tokenRepository, TokenEventRepository tokenEventRepository,
                              AppointmentRepository appointmentRepository, UserRepository userRepository,
                              QueueUpdatePublisher queueUpdatePublisher,
                              ApplicationEventPublisher eventPublisher) {
        this.queueRepository = queueRepository;
        this.counterRepository = counterRepository;
        this.tokenRepository = tokenRepository;
        this.tokenEventRepository = tokenEventRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.queueUpdatePublisher = queueUpdatePublisher;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Token creation
    // -------------------------------------------------------------------------

    @Transactional
    public Token createWalkIn(UUID queueId, UUID customerId, TokenPriority priority, UUID actorId, Instant now) {
        Queue queue = lockedOpenQueue(queueId);
        User customer = user(customerId);
        requireSameOrganization(queue.getOrganization(), customer);
        User actor = actor(actorId, queue.getOrganization());
        int number = queue.allocateNextTokenNumber();
        Token token = new Token(queue.getOrganization(), queue, queue.getService(), customer, null,
                number, displayCode(number), TokenSource.WALK_IN, priorityOrNormal(priority), now);
        tokenRepository.save(token);
        record(token, actor, "TOKEN_CREATED", null, TokenStatus.WAITING, null, now);
        eventPublisher.publishEvent(AuditEvent.success(
                token.getOrganization().getId(), actor.getId(), AuditAction.TOKEN_CREATED, "Token", token.getId(),
                Map.of("tokenNumber", token.getTokenNumber(), "source", "WALK_IN")
        ));
        return token;
    }

    @Transactional
    public Token createAppointmentToken(UUID appointmentId, UUID actorId, Instant now) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found."));
        if (!appointment.isPresent()) {
            throw new ConflictException("Appointment must be checked in before token creation.");
        }
        if (tokenRepository.existsByAppointmentId(appointmentId)) {
            throw new ConflictException("Appointment already has a token.");
        }
        Queue queue = lockedOpenQueue(appointment.getQueue().getId());
        User actor = actor(actorId, queue.getOrganization());
        int number = queue.allocateNextTokenNumber();
        Token token = new Token(queue.getOrganization(), queue, queue.getService(), appointment.getCustomer(), appointment,
                number, displayCode(number), TokenSource.APPOINTMENT, TokenPriority.NORMAL, now);
        tokenRepository.save(token);
        record(token, actor, "TOKEN_CREATED", null, TokenStatus.WAITING, null, now);
        eventPublisher.publishEvent(AuditEvent.success(
                token.getOrganization().getId(), actor.getId(), AuditAction.TOKEN_CREATED, "Token", token.getId(),
                Map.of("tokenNumber", token.getTokenNumber(), "source", "APPOINTMENT")
        ));
        return token;
    }

    // -------------------------------------------------------------------------
    // Call next — the main endpoint implementation
    // -------------------------------------------------------------------------

    /**
     * Atomically selects and claims the highest-priority eligible waiting token for the given
     * counter, transitioning it from {@code WAITING} to {@code CALLED}.
     *
     * <p>Steps (all within one {@code @Transactional} boundary):
     * <ol>
     *   <li>Authenticate staff (actor loaded from DB, organization scope verified).</li>
     *   <li>Lock the counter row with {@code SELECT … FOR UPDATE}.</li>
     *   <li>Verify the counter is active (not {@code INACTIVE}), not paused, and owned by the
     *       requesting staff member.</li>
     *   <li>Verify the counter has no current {@code CALLED} or {@code SERVING} token.</li>
     *   <li>Find eligible waiting tokens via {@link QueueEnginePolicy#eligible} and score/sort
     *       them with {@link QueueEnginePolicy#select} (starvation guard + priority + aging).</li>
     *   <li>Lock the selected token row ({@code SELECT … FOR UPDATE}) and re-check eligibility
     *       under the lock. If another request claimed it first, retry up to
     *       {@value #MAX_CALL_NEXT_RETRIES} times before returning {@code 409}.</li>
     *   <li>Transition the token: {@code status = CALLED}, {@code assigned_counter_id = counterId},
     *       {@code called_at = now}, {@code version++}.</li>
     *   <li>Update the counter to {@code IN_USE}.</li>
     *   <li>Insert a {@code TOKEN_CALLED} token event.</li>
     *   <li>Publish the canonical {@code TOKEN_CALLED} audit event with correlation ID and IP.</li>
     *   <li>Register an after-commit synchronisation to publish the WebSocket {@code TOKEN_CALLED}
     *       message — the message is never sent if the transaction rolls back.</li>
     * </ol>
     *
     * @param counterId   the counter the staff member is operating
     * @param staffUserId the authenticated staff member
     * @param req         HTTP context: correlation ID and remote IP for the audit record
     * @param now         server-side current instant (injected for testability)
     * @return the claimed token
     * @throws ConflictException if the counter is paused/inactive/occupied, no eligible token
     *                           exists, or the retry budget is exhausted
     */
    @Transactional
    public Token callNext(UUID counterId, UUID staffUserId, CallNextRequest req, Instant now) {
        // Step 1 & 2: authenticate staff and acquire counter row lock (Layer 1)
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, counter.getOrganization());

        // Step 3: verify counter is active and not paused
        requireCounterCallable(counter, staff);

        // Step 4: verify the counter has no currently active token
        if (tokenRepository.existsByAssignedCounterIdAndStatusIn(counterId, ACTIVE_COUNTER_STATUSES)) {
            throw new ConflictException("Counter already has an active token.");
        }

        // Steps 5–6: candidate selection with bounded retry (Layers 1+2)
        Token token = selectAndClaimWithRetry(counter, staff, now);

        // Step 8: transition the counter to IN_USE
        counter.markInUse();

        // Step 9: token lifecycle event
        record(token, staff, "TOKEN_CALLED", TokenStatus.WAITING, TokenStatus.CALLED, null, now);

        // Step 10: publish the canonical audit action with request context.
        eventPublisher.publishEvent(AuditEvent.success(
                token.getOrganization().getId(), staff.getId(), AuditAction.TOKEN_CALLED, "Token", token.getId(),
                Map.of("counterId", counterId), req.correlationId(), req.ipAddress(), now
        ));

        // Step 11: publish WebSocket update ONLY after the transaction commits (Layer 3 side effect)
        registerAfterCommit(() -> queueUpdatePublisher.publish(token, counter, "TOKEN_CALLED", now));

        return token;
    }

    /**
     * Runs candidate selection and the token-lock + recheck loop up to
     * {@value #MAX_CALL_NEXT_RETRIES} times. On each attempt:
     * <ol>
     *   <li>Query all {@code WAITING} tokens for the counter's organisation (bounded to
     *       {@link QueueEnginePolicy#MAX_CANDIDATES}).</li>
     *   <li>Filter by eligibility, score, and apply the starvation-guard selection.</li>
     *   <li>Acquire a pessimistic write lock on the selected token row.</li>
     *   <li>Re-check eligibility under the lock (another counter may have claimed it).</li>
     *   <li>On success: apply {@code token.call(counter, now)} (Step 7) and return.</li>
     *   <li>On ineligible recheck: decrement the retry budget and loop.</li>
     * </ol>
     */
    private Token selectAndClaimWithRetry(Counter counter, User staff, Instant now) {
        ConflictException lastConflict = null;

        for (int attempt = 1; attempt <= MAX_CALL_NEXT_RETRIES; attempt++) {
            // Step 5: build candidate list, score, and select winner
            List<QueueEnginePolicy.CandidateScore> candidates = tokenRepository
                    .findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(
                            counter.getOrganization().getId(), TokenStatus.WAITING)
                    .stream()
                    .filter(t -> QueueEnginePolicy.eligible(t, counter, now))
                    .map(t -> QueueEnginePolicy.score(t, now))
                    .toList();

            QueueEnginePolicy.CandidateScore selected = QueueEnginePolicy.select(candidates);
            if (selected == null) {
                throw new ConflictException("No eligible token is available for this counter.");
            }

            // Step 6: acquire token row lock (Layer 2) then recheck under the lock
            Token token = tokenRepository.findWithLockById(selected.token().getId())
                    .orElseThrow(() -> new ConflictException(
                            "Selected token was removed before it could be claimed."));

            if (!QueueEnginePolicy.eligible(token, counter, now)) {
                // Another counter claimed this token between our read and our lock.
                // Record the conflict and retry with the next-best candidate.
                lastConflict = new ConflictException(
                        "Selected token is no longer eligible (attempt " + attempt + ").");
                continue;
            }

            // Step 7: atomic state transition — WAITING → CALLED
            token.call(counter, now);
            return token;
        }

        // Retry budget exhausted — every candidate was stolen by another concurrent counter.
        throw lastConflict != null ? lastConflict
                : new ConflictException("No eligible token is available for this counter.");
    }

    // -------------------------------------------------------------------------
    // Remaining lifecycle operations
    // -------------------------------------------------------------------------

    @Transactional
    public Token startService(UUID tokenId, UUID counterId, UUID staffUserId, Instant now) {
        Token token = lockedToken(tokenId);
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, token.getOrganization());
        requireCounterCallable(counter, staff);
        requireAssigned(token, counter);
        requireState(token, TokenStatus.CALLED);
        token.startService(now);
        record(token, staff, "SERVICE_STARTED", TokenStatus.CALLED, TokenStatus.SERVING, null, now);
        publishAfterCommit(token, counter, "SERVICE_STARTED", now);
        return token;
    }

    @Transactional
    public Token cancel(UUID tokenId, UUID actorId, String reason, Instant now) {
        Token token = lockedToken(tokenId);
        User actor = actor(actorId, token.getOrganization());
        requireReason(reason);
        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new ConflictException("Only waiting or called tokens can be cancelled.");
        }
        TokenStatus previous = token.getStatus();
        Counter assignedCounter = token.getAssignedCounter();
        token.cancel(reason);
        if (token.getAppointment() != null) token.getAppointment().cancel();
        releaseCounter(token);
        record(token, actor, "TOKEN_CANCELLED", previous, TokenStatus.CANCELLED, reason, now);
        // Notify queue subscribers that queue composition changed
        publishQueueUpdatedAfterCommit(token, assignedCounter, now);
        return token;
    }

    @Transactional
    public Token noShow(UUID tokenId, UUID actorId, String reason, Instant now) {
        Token token = lockedToken(tokenId);
        User actor = actor(actorId, token.getOrganization());
        requireReason(reason);
        TokenStatus previous = token.getStatus();
        if (previous == TokenStatus.CALLED) {
            Instant eligibleAt = token.getCalledAt().plus(Duration.ofMinutes(token.getService().getGracePeriodMinutes()));
            if (now.isBefore(eligibleAt)) throw new ConflictException("No-show grace period has not elapsed.");
        } else if (previous != TokenStatus.WAITING) {
            throw new ConflictException("Only waiting or called tokens can be marked no-show.");
        }
        Counter assignedCounter = token.getAssignedCounter();
        token.noShow(reason);
        if (token.getAppointment() != null) token.getAppointment().markNoShow();
        releaseCounter(token);
        record(token, actor, "TOKEN_NO_SHOW", previous, TokenStatus.NO_SHOW, reason, now);
        publishQueueUpdatedAfterCommit(token, assignedCounter, now);
        return token;
    }

    @Transactional
    public Token requeue(UUID tokenId, UUID counterId, UUID staffUserId, String reason, Instant now) {
        Token token = lockedToken(tokenId);
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, token.getOrganization());
        requireReason(reason);
        requireCounterCallable(counter, staff);
        requireAssigned(token, counter);
        requireState(token, TokenStatus.CALLED);
        token.requeue(reason);
        counter.release();
        record(token, staff, "TOKEN_REQUEUED", TokenStatus.CALLED, TokenStatus.WAITING, reason, now);
        // Requeue changes queue composition — broadcast QUEUE_UPDATED
        publishQueueUpdatedAfterCommit(token, counter, now);
        return token;
    }

    @Transactional
    public Token skip(UUID tokenId, UUID counterId, UUID staffUserId, String reason, Instant now) {
        Token token = lockedToken(tokenId);
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, token.getOrganization());
        requireReason(reason);
        requireCounterCallable(counter, staff);
        requireAssigned(token, counter);
        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.SERVING) {
            throw new ConflictException("Only active tokens can be skipped.");
        }
        TokenStatus previous = token.getStatus();
        token.skip(reason);
        counter.release();
        record(token, staff, "TOKEN_SKIPPED", previous, TokenStatus.SKIPPED, reason, now);
        eventPublisher.publishEvent(AuditEvent.success(
                token.getOrganization().getId(), staff.getId(), AuditAction.TOKEN_SKIPPED, "Token", token.getId(),
                Map.of("reason", reason)
        ));
        publishAfterCommit(token, counter, "TOKEN_SKIPPED", now);
        return token;
    }

    @Transactional
    public Token complete(UUID tokenId, UUID counterId, UUID staffUserId, Instant now) {
        Token token = lockedToken(tokenId);
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, token.getOrganization());
        requireCounterCallable(counter, staff);
        requireAssigned(token, counter);
        requireState(token, TokenStatus.SERVING);
        token.complete(now);
        if (token.getAppointment() != null) token.getAppointment().complete();
        counter.release();
        record(token, staff, "SERVICE_COMPLETED", TokenStatus.SERVING, TokenStatus.COMPLETED, null, now);
        eventPublisher.publishEvent(AuditEvent.success(
                token.getOrganization().getId(), staff.getId(), AuditAction.TOKEN_COMPLETED, "Token", token.getId(),
                Map.of("serviceId", token.getService().getId())
        ));
        publishAfterCommit(token, counter, "SERVICE_COMPLETED", now);
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<Token> findNext(UUID counterId, Instant now) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found."));
        if (counter.getStatus() == CounterStatus.PAUSED || counter.getStatus() == CounterStatus.INACTIVE) return Optional.empty();
        return tokenRepository.findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(
                        counter.getOrganization().getId(), TokenStatus.WAITING).stream()
                .filter(token -> QueueEnginePolicy.eligible(token, counter, now))
                .map(token -> QueueEnginePolicy.score(token, now))
            .max(QueueEnginePolicy.ordering())
            .map(QueueEnginePolicy.CandidateScore::token);
    }

    // -------------------------------------------------------------------------
    // Counter lifecycle operations
    // -------------------------------------------------------------------------

    /**
     * Pauses a counter so it stops accepting new tokens.
     *
     * @param counterId   the counter to pause
     * @param staffUserId the authenticated staff member requesting the pause
     * @param reason      mandatory reason (enforced by DB check constraint)
     * @return the updated counter
     */
    @Transactional
    public Counter pauseCounter(UUID counterId, UUID staffUserId, String reason) {
        requireReason(reason);
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, counter.getOrganization());
        counter.pause(reason);
        eventPublisher.publishEvent(AuditEvent.success(
                counter.getOrganization().getId(), staff.getId(),
                AuditAction.COUNTER_PAUSED, "Counter", counter.getId(),
                Map.of("reason", reason)
        ));
        return counter;
    }

    /**
     * Resumes a paused counter, returning it to {@code AVAILABLE} status.
     *
     * @param counterId   the counter to resume
     * @param staffUserId the authenticated staff member requesting the resume
     * @return the updated counter
     */
    @Transactional
    public Counter resumeCounter(UUID counterId, UUID staffUserId) {
        Counter counter = lockedCounter(counterId);
        User staff = actor(staffUserId, counter.getOrganization());
        counter.resume();
        eventPublisher.publishEvent(AuditEvent.success(
                counter.getOrganization().getId(), staff.getId(),
                AuditAction.COUNTER_RESUMED, "Counter", counter.getId(),
                Map.of()
        ));
        return counter;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Queue lockedOpenQueue(UUID queueId) {
        return queueRepository.findWithLockByIdAndStatus(queueId, QueueStatus.OPEN)
                .orElseThrow(() -> new ConflictException("Queue is missing or not open."));
    }

    private Counter lockedCounter(UUID counterId) {
        return counterRepository.findWithLockById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found."));
    }

    private Token lockedToken(UUID tokenId) {
        return tokenRepository.findWithLockById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found."));
    }

    private User user(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    private User actor(UUID actorId, Organization organization) {
        User actor = user(actorId);
        requireSameOrganization(organization, actor);
        return actor;
    }

    private void requireSameOrganization(Organization organization, User user) {
        if (!organization.getId().equals(user.getOrganization().getId())) {
            throw new ConflictException("Organization scope mismatch.");
        }
    }

    /**
     * Verifies that the counter can accept a call-next or service command.
     * Produces distinct error messages for {@code INACTIVE} vs {@code PAUSED} so
     * clients and operators can identify the specific blocker.
     */
    private void requireCounterCallable(Counter counter, User staff) {
        if (counter.getStatus() == CounterStatus.INACTIVE) {
            throw new ConflictException("Counter is inactive.");
        }
        if (counter.getStatus() == CounterStatus.PAUSED) {
            throw new ConflictException("Counter is paused.");
        }
        if (!counter.isAssignedTo(staff)) {
            throw new ConflictException("Counter is not assigned to this staff member.");
        }
    }

    private void requireAssigned(Token token, Counter counter) {
        if (token.getAssignedCounter() == null || !token.getAssignedCounter().getId().equals(counter.getId())) {
            throw new ConflictException("Token is assigned to another counter.");
        }
    }

    private void requireState(Token token, TokenStatus expected) {
        if (token.getStatus() != expected) throw new ConflictException("Token is not in the required state.");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new ConflictException("A reason is required.");
    }

    private void releaseCounter(Token token) {
        if (token.getAssignedCounter() != null) token.getAssignedCounter().release();
    }

    private void record(Token token, User actor, String eventType, TokenStatus from, TokenStatus to,
                        String reason, Instant now) {
        tokenEventRepository.save(new TokenEvent(token, token.getOrganization(), actor, eventType, from, to, reason, now));
    }

    /**
     * Registers an after-commit synchronisation that publishes a token state change event
     * to the WebSocket broker. The event is not sent if the transaction rolls back.
     *
     * @param token     the token involved
     * @param counter   the counter involved
     * @param eventType semantic event name (e.g. "SERVICE_STARTED", "TOKEN_SKIPPED")
     * @param now       the instant of the operation
     */
    private void publishAfterCommit(Token token, Counter counter, String eventType, Instant now) {
        registerAfterCommit(() -> queueUpdatePublisher.publish(token, counter, eventType, now));
    }

    /**
     * Publishes a {@code QUEUE_UPDATED} event after commit when queue composition changes
     * (token cancelled, no-showed, or re-queued). The counter may be null if the token was
     * never assigned — in that case, a synthetic counter ID placeholder is used for routing.
     */
    private void publishQueueUpdatedAfterCommit(Token token, Counter counterBeforeChange, Instant now) {
        // Use the counter that was assigned (before release) for topic routing.
        // If it was never assigned, use null — the publisher handles this gracefully.
        registerAfterCommit(() -> queueUpdatePublisher.publishQueueUpdated(token, counterBeforeChange, now));
    }

    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String displayCode(int number) { return "T-" + String.format("%04d", number); }
    private TokenPriority priorityOrNormal(TokenPriority priority) { return priority == null ? TokenPriority.NORMAL : priority; }
}
