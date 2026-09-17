package com.queuewise.queue.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket / STOMP broker configuration for QueueWise.
 *
 * <h3>Endpoint</h3>
 * {@code /ws} — upgraded to WebSocket using the native protocol.
 * Allowed origins are configured from {@code queuewise.cors.allowed-origins}.
 *
 * <h3>STOMP broker</h3>
 * An in-process simple broker with prefix {@code /topic} and {@code /user}.
 * Application messages use prefix {@code /app}.
 *
 * <h3>Topics — REALTIME_DESIGN.md §5</h3>
 * <pre>
 *   /topic/organizations/{organizationId}/queues/{serviceId}  — queue/staff view
 *   /topic/counters/{counterId}                               — counter-specific staff view
 *   /user/queue/token-updates                                 — personal customer token updates
 * </pre>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>{@link WebSocketAuthChannelInterceptor} — validates JWT on STOMP CONNECT.</li>
 *   <li>{@link WebSocketSubscriptionInterceptor} — enforces org/resource scope on SUBSCRIBE.</li>
 * </ul>
 *
 * <h3>Heartbeats</h3>
 * Server-to-client: 25 000 ms. Client-to-server: 25 000 ms.
 * Stale sessions are closed by the broker after missing the negotiated heartbeat.
 */
@Configuration
@EnableWebSocketMessageBroker
public class QueueWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final WebSocketSubscriptionInterceptor subscriptionInterceptor;
    private final String allowedOrigins;

    public QueueWebSocketConfig(
            WebSocketAuthChannelInterceptor authChannelInterceptor,
            WebSocketSubscriptionInterceptor subscriptionInterceptor,
            @Value("${queuewise.cors.allowed-origins}") String allowedOrigins) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.subscriptionInterceptor = subscriptionInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .toArray(String[]::new);
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[]{25_000, 25_000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /** Register the JWT auth interceptor on the inbound channel (CONNECT / SUBSCRIBE frames). */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor, subscriptionInterceptor);
    }
}