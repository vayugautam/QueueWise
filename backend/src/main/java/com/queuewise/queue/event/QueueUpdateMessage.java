package com.queuewise.queue.event;

import com.queuewise.token.domain.TokenStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound WebSocket message DTO delivered to STOMP subscribers.
 *
 * <p>Follows the stable envelope contract from REALTIME_DESIGN.md §7.
 * Sensitive fields (customer PII, JWTs) are never included.
 * Clients should treat {@code version} as a monotonic counter and discard
 * messages whose version is older than the version they have already rendered.
 */
public record QueueUpdateMessage(
        UUID eventId,
        String eventType,
        UUID organizationId,
        UUID queueId,
        UUID serviceId,
        UUID tokenId,
        UUID counterId,
        TokenStatus previousStatus,
        TokenStatus status,
        long version,
        Instant occurredAt) { }