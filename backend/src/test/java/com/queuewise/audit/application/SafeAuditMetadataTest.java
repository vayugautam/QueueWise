package com.queuewise.audit.application;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeAuditMetadataTest {

    @Test
    void sanitizeRemovesCredentialAndTokenFields() {
        Map<String, Object> metadata = SafeAuditMetadata.sanitize(Map.of(
                "password", "do-not-store",
                "access_token", "jwt-value",
                "Authorization", "Bearer secret",
                "counterId", "counter-1",
                "source", "WALK_IN"));

        assertThat(metadata).containsKeys("counterId", "source");
        assertThat(metadata).doesNotContainKeys("password", "access_token", "Authorization");
    }

    @Test
    void sanitizeHandlesSecretKeyVariants() {
        Map<String, Object> metadata = SafeAuditMetadata.sanitize(Map.of(
                "client-secret", "secret",
                "refreshToken", "refresh-token",
                "loginMethod", "EMAIL"));

        assertThat(metadata).containsEntry("loginMethod", "EMAIL");
        assertThat(metadata).doesNotContainKeys("client-secret", "refreshToken");
    }
}