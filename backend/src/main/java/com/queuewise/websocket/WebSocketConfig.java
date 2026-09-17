package com.queuewise.websocket;

import com.queuewise.security.JwtTokenService;
import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import com.queuewise.user.persistence.UserRepository;
import io.jsonwebtoken.Claims;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the STOMP over WebSocket message broker.
 *
 * <h3>Transport</h3>
 * Registers {@code /ws} as the SockJS endpoint. SockJS provides HTTP long-polling fallback for
 * environments (corporate proxies, load balancers) that do not support raw WebSocket.
 *
 * <h3>Broker</h3>
 * Uses Spring's built-in in-memory simple broker on the {@code /topic} prefix.  Swap for a
 * RabbitMQ/ActiveMQ relay when horizontal scaling is required.
 *
 * <h3>Authentication</h3>
 * A {@link ChannelInterceptor} intercepts every STOMP {@code CONNECT} frame. It reads the JWT
 * from the {@code Authorization} STOMP header, validates it with {@link JwtTokenService}, and
 * sets a fully-populated {@link UsernamePasswordAuthenticationToken} as the user on the
 * WebSocket session. All subsequent messages in that session inherit this principal.
 *
 * <p>If the JWT is absent or invalid the interceptor throws {@link IllegalArgumentException},
 * which Spring WebSocket translates to a STOMP {@code ERROR} frame and closes the connection —
 * no subscription is established for unauthenticated callers.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final String allowedOrigins;

    public WebSocketConfig(JwtTokenService jwtTokenService,
                           UserRepository userRepository,
                           @Value("${queuewise.cors.allowed-origins}") String allowedOrigins) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Register the SockJS handshake endpoint and allow the configured CORS origins.
     *
     * <p>The {@code /ws} path is intentionally NOT listed under the HTTP security filter chain
     * ({@code .permitAll()}) — STOMP-level auth via the channel interceptor below is the gate.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    /**
     * Configure the in-memory message broker.
     *
     * <ul>
     *   <li>{@code /topic} — server-to-client broadcasts (queue and counter updates)</li>
     *   <li>{@code /app}   — client-to-server messages (reserved for future use)</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Intercepts the inbound channel to authenticate STOMP CONNECT frames using a JWT.
     *
     * <p>The STOMP client must send the JWT in the {@code Authorization} header:
     * <pre>
     * CONNECT
     * Authorization:Bearer &lt;jwt&gt;
     * accept-version:1.2
     * </pre>
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Pass-through for non-CONNECT frames (SUBSCRIBE, SEND, DISCONNECT, etc.)
                    return message;
                }

                // Read Authorization from STOMP native headers
                List<String> authHeaders = accessor.getNativeHeader("Authorization");
                if (authHeaders == null || authHeaders.isEmpty()) {
                    throw new IllegalArgumentException("Missing Authorization header on STOMP CONNECT.");
                }

                String authHeader = authHeaders.get(0);
                if (!authHeader.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Authorization header must start with 'Bearer '.");
                }

                String jwt = authHeader.substring(7);
                try {
                    Claims claims = jwtTokenService.parse(jwt);
                    UUID userId = UUID.fromString(claims.getSubject());
                    UUID organizationId = UUID.fromString(claims.get("org", String.class));

                    User user = userRepository.findByIdAndOrganizationIdAndStatus(
                                    userId, organizationId, UserStatus.ACTIVE)
                            .orElseThrow(() -> new IllegalArgumentException("Inactive or unknown user."));

                    QueueWiseUserPrincipal principal = QueueWiseUserPrincipal.from(user);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    accessor.setUser(auth);

                    log.debug("WebSocket CONNECT authenticated: userId={} orgId={}", userId, organizationId);
                } catch (RuntimeException ex) {
                    log.warn("WebSocket CONNECT rejected: {}", ex.getMessage());
                    throw new IllegalArgumentException("Invalid or expired JWT: " + ex.getMessage(), ex);
                }

                return message;
            }
        });
    }
}
