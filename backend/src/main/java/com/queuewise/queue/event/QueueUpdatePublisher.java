package com.queuewise.queue.event;

import com.queuewise.counter.domain.Counter;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes committed queue state changes as WebSocket messages.
 *
 * <h3>Publication timing guarantee</h3>
 * {@link #publish} is called from inside a {@code @Transactional} service method. It records a
 * Spring application event. The {@link #publishAfterCommit} listener is annotated with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, which means the actual WebSocket
 * broadcast fires <em>only after</em> the database transaction has successfully committed. If the
 * transaction rolls back for any reason the listener is never invoked, so no false update is
 * delivered to clients — satisfying CONCURRENCY.md §12 and REALTIME_DESIGN.md §8.
 *
 * <h3>Topics</h3>
 * Follows REALTIME_DESIGN.md §5:
 * <ul>
 *   <li>{@code /topic/organizations/{organizationId}/queues/{serviceId}} — queue/staff views</li>
 *   <li>{@code /topic/counters/{counterId}} — counter-specific staff view</li>
 * </ul>
 */
@Component
public class QueueUpdatePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueUpdatePublisher(ApplicationEventPublisher applicationEventPublisher,
                                SimpMessagingTemplate messagingTemplate) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Records a queue-update event against the current transaction. The WebSocket broadcast is
     * deferred to {@link #publishAfterCommit} and fires only on successful commit.
     *
     * @param token       the token whose status changed
     * @param counter     the counter involved in the transition
     * @param eventType   semantic event type string, e.g. {@code "TOKEN_CALLED"}
     * @param occurredAt  server-side timestamp of the transition
     */
    public void publish(Token token, Counter counter, String eventType, Instant occurredAt) {
        applicationEventPublisher.publishEvent(new QueueUpdateEvent(
                UUID.randomUUID(),
                eventType,
                token.getId(),
                token.getQueue().getId(),
                token.getOrganization().getId(),
                token.getService().getId(),
                counter == null ? null : counter.getId(),
                TokenStatus.WAITING,
                token.getStatus(),
                token.getVersion(),
                occurredAt));
    }

        /** Publishes a queue composition change, which may have no counter assignment. */
        public void publishQueueUpdated(Token token, Counter counter, Instant occurredAt) {
                publish(token, counter, "QUEUE_UPDATED", occurredAt);
        }

    /**
     * Delivers the committed update to all relevant STOMP subscribers.
     * Runs outside any database transaction — after commit only.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(QueueUpdateEvent event) {
        QueueUpdateMessage message = new QueueUpdateMessage(
                event.eventId(),
                event.eventType(),
                event.organizationId(),
                event.queueId(),
                event.serviceId(),
                event.tokenId(),
                event.counterId(),
                event.previousStatus(),
                event.status(),
                event.version(),
                event.occurredAt());

        // Queue / service topic — staff queue views and authorized customers
        messagingTemplate.convertAndSend(
                "/topic/organizations/" + event.organizationId() + "/queues/" + event.serviceId(),
                message);

        // Counter topic — the staff member at this specific counter
        messagingTemplate.convertAndSend(
                "/topic/counters/" + event.counterId(),
                message);
    }
}