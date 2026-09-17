package com.queuewise.counter.web;

import com.queuewise.queue.application.CallNextRequest;
import com.queuewise.queue.application.QueueEngineService;
import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.token.domain.Token;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for counter-scoped queue operations.
 *
 * <h2>POST /api/counters/{counterId}/queue/next</h2>
 * <p>Requires the authenticated user to hold one of the staff-level roles. The JWT is validated
 * by {@link com.queuewise.security.JwtAuthenticationFilter} before this method is reached.
 * Method-level {@code @PreAuthorize} enforces role membership.
 *
 * <p>The controller is deliberately thin:
 * <ul>
 *   <li>Extracts the authenticated principal's user ID from the Spring Security context.</li>
 *   <li>Reads the {@code X-Correlation-Id} request header (generates one if absent) and the
 *       remote address for use in the audit record.</li>
 *   <li>Delegates all business logic to {@link QueueEngineService#callNext}.</li>
 *   <li>Maps the returned {@link Token} to a {@link QueueTokenResponse} DTO and returns
 *       {@code 200 OK}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/counters")
@Tag(name = "Counter Queue", description = "Staff queue operations")
@SecurityRequirement(name = "BearerAuth")
public class CounterQueueController {

    private final QueueEngineService queueEngineService;

    public CounterQueueController(QueueEngineService queueEngineService) {
        this.queueEngineService = queueEngineService;
    }

    /**
     * Selects and atomically claims the next eligible waiting token for the given counter.
     *
     * <p>Authentication: JWT bearer token validated by the filter chain.
     * <p>Authorisation: caller must have role {@code RECEPTIONIST}, {@code STAFF},
     * {@code SUPERVISOR}, or {@code ADMIN}.
     * <p>Concurrency: if two requests arrive simultaneously, exactly one succeeds; the other
     * receives {@code 409 CONFLICT}. See {@link QueueEngineService#callNext} for the full
     * concurrency contract.
     *
     * @param counterId      the counter UUID from the path
     * @param authentication injected by Spring Security — must be a {@link QueueWiseUserPrincipal}
     * @param request        the raw HTTP request, used to extract the correlation ID and remote IP
     * @return {@code 200 OK} with the called token details
     */
    @PostMapping("/{counterId}/queue/next")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'STAFF', 'SUPERVISOR', 'ADMIN')")
        @Operation(summary = "Call the next token", description = "Atomically selects and calls the highest-priority eligible token for the counter. Requires a JWT and RECEPTIONIST, STAFF, SUPERVISOR, or ADMIN role. Organization and counter assignment are checked server-side.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token called", content = @Content(mediaType = "application/json", schema = @Schema(implementation = QueueTokenResponse.class), examples = @ExampleObject(value = "{\"id\":\"uuid\",\"displayCode\":\"T-0007\",\"status\":\"CALLED\",\"counterId\":\"uuid\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Role or counter assignment is insufficient", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Counter or actor not found", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Counter unavailable, occupied, or no eligible token", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
        })
    public ResponseEntity<QueueTokenResponse> callNext(
            @PathVariable UUID counterId,
            Authentication authentication,
            HttpServletRequest request) {

        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();

        // Prefer the client-supplied correlation ID so distributed traces remain consistent.
        // Fall back to a generated UUID so the audit record is never left empty.
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        CallNextRequest callNextRequest = new CallNextRequest(correlationId, request.getRemoteAddr());

        Token token = queueEngineService.callNext(counterId, principal.getUserId(), callNextRequest, Instant.now());

        return ResponseEntity.ok(QueueTokenResponse.from(token));
    }

    /**
     * Stable, safe response DTO for a successfully called token.
     *
     * <p>Deliberately excludes customer PII (name, email, phone). The {@code customerId} UUID
     * allows authorised callers to fetch customer details through the customer API if required.
     */
    public record QueueTokenResponse(
            UUID id,
            UUID organizationId,
            UUID queueId,
            UUID serviceId,
            UUID customerId,
            UUID counterId,
            int tokenNumber,
            String displayCode,
            String source,
            String priority,
            String status,
            Instant arrivalAt,
            Instant calledAt,
            long version) {

        static QueueTokenResponse from(Token token) {
            return new QueueTokenResponse(
                    token.getId(),
                    token.getOrganization().getId(),
                    token.getQueue().getId(),
                    token.getService().getId(),
                    token.getCustomer().getId(),
                    token.getAssignedCounter().getId(),
                    token.getTokenNumber(),
                    token.getDisplayCode(),
                    token.getSource().name(),
                    token.getPriority().name(),
                    token.getStatus().name(),
                    token.getArrivalAt(),
                    token.getCalledAt(),
                    token.getVersion());
        }
    }
}