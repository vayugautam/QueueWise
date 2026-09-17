package com.queuewise.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void givenConflictAndCorrelationHeader_whenHandled_thenSafeConflictResponseEchoesCorrelation() {
        // Given
        MockHttpServletRequest request = request("/api/counters/1", "trace-123");

        // When
        var response = handler.handleConflict(new ConflictException("Counter is paused."), request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
        assertThat(response.getBody().correlationId()).isEqualTo("trace-123");
    }

    @Test
    void givenUnexpectedExceptionWithoutCorrelation_whenHandled_thenInternalDetailsAreHidden() {
        // Given
        MockHttpServletRequest request = request("/api/unknown", null);

        // When
        var response = handler.handleUnexpected(new IllegalStateException("database password"), request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("password");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    private MockHttpServletRequest request(String path, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (correlationId != null) request.addHeader("X-Correlation-Id", correlationId);
        return request;
    }
}
