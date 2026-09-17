package com.queuewise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenLifetime;

    public JwtTokenService(
            @Value("${queuewise.jwt.secret}") String secret,
            @Value("${queuewise.jwt.issuer}") String issuer,
            @Value("${queuewise.jwt.audience}") String audience,
            @Value("${queuewise.jwt.access-token-minutes}") long accessTokenMinutes) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenLifetime = Duration.ofMinutes(accessTokenMinutes);
    }

    public IssuedToken issue(QueueWiseUserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenLifetime);
        String token = Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(principal.getUserId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim("org", principal.getOrganizationId().toString())
                .claim("roles", principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .toList())
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedToken(String value, Instant expiresAt) { }
}
