package com.queuewise.queue.event;

import com.queuewise.security.QueueWiseUserPrincipal;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Enforces per-subscription scope rules after authentication is established.
 *
 * <h3>Rules (per REALTIME_DESIGN.md §6)</h3>
 * <ul>
 *   <li>{@code /topic/organizations/{orgId}/queues/{serviceId}} — subscriber's
 *       {@code organizationId} in their JWT principal must match the path's {@code orgId}.</li>
 *   <li>{@code /topic/counters/{counterId}} — subscriber must be authenticated (counter-level
 *       staff scope validation happens at REST; WebSocket only verifies org membership here).</li>
 *   <li>{@code /user/queue/token-updates} — always permitted for authenticated users
 *       (Spring routes this to the individual's session automatically).</li>
 *   <li>Any other topic is rejected.</li>
 * </ul>
 *
 * <p>Rejected SUBSCRIBE frames throw a {@link SecurityException}, which Spring converts to an
 * ERROR frame and closes the subscription without revealing whether the resource exists.
 */
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        Principal user = accessor.getUser();
        if (user == null || (user instanceof AnonymousAuthenticationToken)) {
            throw new SecurityException("Unauthenticated WebSocket subscription rejected.");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new SecurityException("Empty subscription destination rejected.");
        }

        // /user/queue/token-updates — personal destination, always allowed for authenticated users
        if (destination.startsWith("/user/queue/")) {
            return message;
        }

        // /topic/organizations/{orgId}/queues/{serviceId} — org-scope check
        if (destination.startsWith("/topic/organizations/")) {
            String orgIdFromPath = extractOrganizationId(destination);
            String userOrgId = organizationIdOf(user);
            if (!userOrgId.equals(orgIdFromPath)) {
                throw new SecurityException(
                        "Subscription to organization topic denied: organization scope mismatch.");
            }
            return message;
        }

        // /topic/counters/{counterId} — any authenticated org member may subscribe;
        // detailed counter assignment is enforced by REST, not WebSocket.
        if (destination.startsWith("/topic/counters/")) {
            return message;
        }

        // All other topics are denied
        throw new SecurityException("Unknown subscription destination: " + destination);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the organization UUID segment from a path like
     * {@code /topic/organizations/{orgId}/queues/{serviceId}}.
     */
    private String extractOrganizationId(String destination) {
        // destination = /topic/organizations/<orgId>/queues/<serviceId>
        String[] parts = destination.split("/");
        // parts: ["", "topic", "organizations", orgId, "queues", serviceId]
        if (parts.length < 4) {
            throw new SecurityException("Malformed organization topic path: " + destination);
        }
        return parts[3];
    }

    private String organizationIdOf(Principal user) {
        if (user instanceof Authentication auth &&
                auth.getPrincipal() instanceof QueueWiseUserPrincipal principal) {
            return principal.getOrganizationId().toString();
        }
        throw new SecurityException("Cannot determine organization from principal.");
    }
}
