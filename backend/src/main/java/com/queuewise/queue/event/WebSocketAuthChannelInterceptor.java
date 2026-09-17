package com.queuewise.queue.event;

import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import com.queuewise.user.persistence.UserRepository;
import com.queuewise.security.JwtTokenService;
import com.queuewise.security.QueueWiseUserPrincipal;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Validates the JWT carried in the STOMP {@code CONNECT} frame's {@code Authorization} header
 * and sets the authenticated principal on the STOMP session.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Client sends a STOMP {@code CONNECT} frame with header
 *       {@code Authorization: Bearer <jwt>}.</li>
 *   <li>This interceptor parses and validates the token using {@link JwtTokenService#parse}.</li>
 *   <li>The corresponding active user is loaded from the database.</li>
 *   <li>A {@link UsernamePasswordAuthenticationToken} is set as the frame's user principal —
 *       Spring propagates this to subscription / message handling automatically.</li>
 *   <li>On any failure the frame is rejected with a {@link BadCredentialsException}, which Spring
 *       converts to a STOMP ERROR frame and closes the connection.</li>
 * </ol>
 *
 * <p>This interceptor only establishes identity. Per-subscription scope rules are enforced by
 * {@link WebSocketSubscriptionInterceptor}.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public WebSocketAuthChannelInterceptor(JwtTokenService jwtTokenService,
                                           UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException(
                    "WebSocket CONNECT requires a valid Bearer token in the Authorization header.");
        }

        try {
            Claims claims = jwtTokenService.parse(authHeader.substring(7));
            UUID userId = UUID.fromString(claims.getSubject());
            UUID organizationId = UUID.fromString(claims.get("org", String.class));

            User user = userRepository
                    .findByIdAndOrganizationIdAndStatus(userId, organizationId, UserStatus.ACTIVE)
                    .orElseThrow(() -> new BadCredentialsException("User account is inactive."));

            QueueWiseUserPrincipal principal = QueueWiseUserPrincipal.from(user);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(auth);

        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadCredentialsException("WebSocket JWT validation failed: " + ex.getMessage(), ex);
        }

        return message;
    }
}
