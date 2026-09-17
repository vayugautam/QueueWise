package com.queuewise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtTokenServiceTest {

    private final JwtTokenService tokenService = new JwtTokenService(
            "12345678901234567890123456789012",
            "queuewise-api",
            "queuewise-web",
            15);

    @Test
    void issuedTokenCanBeParsed() {
        QueueWiseUserPrincipal principal = principal();

        JwtTokenService.IssuedToken issued = tokenService.issue(principal);

        assertThat(tokenService.parse(issued.value()).getSubject()).isEqualTo(principal.getUserId().toString());
    }

    @Test
    void modifiedTokenIsRejected() {
        String token = tokenService.issue(principal()).value();

        assertThatThrownBy(() -> tokenService.parse(token + "tampered"))
                .isInstanceOf(RuntimeException.class);
    }

    private QueueWiseUserPrincipal principal() {
        return new QueueWiseUserPrincipalForTest(UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class QueueWiseUserPrincipalForTest extends QueueWiseUserPrincipal {
        private QueueWiseUserPrincipalForTest(UUID userId, UUID organizationId) {
            super(userId, organizationId, "test@example.com", "hash", true,
                    Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        }
    }
}
