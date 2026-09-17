package com.queuewise.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * STOMP topic-level security using Spring Security 6's modern
 * {@link AuthorizationManager}-based API (replaces the removed
 * {@code AbstractSecurityWebSocketMessageBrokerConfigurer}).
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>{@code /topic/organizations/**} — any authenticated user (staff and authorised customers)</li>
 *   <li>{@code /topic/counters/**}      — authenticated users only (staff role enforced at
 *       subscription; counter ownership is validated server-side before the subscribe is accepted)</li>
 *   <li>{@code /app/**}                 — any authenticated user (reserved for future client→server)</li>
 *   <li>All other destinations          — deny</li>
 * </ul>
 *
 * <p>CSRF is disabled for WebSocket frames because the JWT on the STOMP CONNECT frame provides
 * equivalent origin-bound authentication — see {@code @EnableWebSocketSecurity}'s
 * {@code sameOriginDisabled} attribute.
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    /**
     * Configures destination-level authorization for inbound STOMP messages.
     *
     * <p>Spring Security's {@code @EnableWebSocketSecurity} picks up this bean automatically and
     * applies it to every inbound channel message before the message is routed.
     */
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {

        messages
                // Organization-scoped queue/service topics — all authenticated users
                .simpSubscribeDestMatchers("/topic/organizations/**").authenticated()
                // Counter-specific topics — staff only (RECEPTIONIST, STAFF, SUPERVISOR, ADMIN)
                .simpSubscribeDestMatchers("/topic/counters/**")
                    .hasAnyRole("RECEPTIONIST", "STAFF", "SUPERVISOR", "ADMIN")
                // Client-to-server app destinations (future)
                .simpDestMatchers("/app/**").authenticated()
                // Deny everything else
                .anyMessage().denyAll();

        return messages.build();
    }
}
