package com.queuewise.queue.event;

import com.queuewise.token.domain.TokenStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal Spring application event published within an active transaction.
 * The {@link QueueUpdatePublisher} listener is annotated with
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, so the WebSocket broadcast
 * is deferred until the database transaction durably commits.
 *
 * <p>Fields follow the REALTIME_DESIGN.md §7 message envelope contract.
 */
public record QueueUpdateEvent(
        UUID eventId,
        String eventType,
        UUID tokenId,
        UUID queueId,
        UUID organizationId,
        UUID serviceId,
        UUID counterId,
        TokenStatus previousStatus,
        TokenStatus status,
        long version,
        Instant occurredAt) { }