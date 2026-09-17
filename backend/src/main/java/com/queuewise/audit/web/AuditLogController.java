package com.queuewise.audit.web;

import com.queuewise.audit.persistence.AuditLogRepository;
import com.queuewise.security.QueueWiseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for querying the immutable audit log.
 *
 * <h3>Access control</h3>
 * <ul>
 *   <li>ADMIN and SUPERVISOR roles may read all logs for their organisation.</li>
 *   <li>Callers are scoped to their own organisation — cross-org reads are impossible
 *       because the organisation ID is extracted from the authenticated principal.</li>
 * </ul>
 *
 * <h3>Pagination</h3>
 * Results are always ordered newest-first. The default page size is 50; maximum is 200.
 */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "Immutable audit trail for all important system events")
@SecurityRequirement(name = "BearerAuth")
public class AuditLogController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Returns the paginated audit log for the caller's organisation.
     *
     * <p>Optional filters:
     * <ul>
     *   <li>{@code action}   — restrict to a specific action (e.g. {@code LOGIN})</li>
     *   <li>{@code entityType} + {@code entityId} — full audit trail for one resource</li>
     * </ul>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(
        summary = "List audit logs",
        description = """
            Returns the paginated, newest-first audit log for the caller's organisation.
            Optionally filter by action name or resource type + ID.
            Roles required: ADMIN or SUPERVISOR.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit log page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid UUID or pagination value", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            Authentication authentication,

            @Parameter(description = "Filter by action name, e.g. LOGIN, TOKEN_CALLED")
            @RequestParam(required = false) String action,

            @Parameter(description = "Resource type filter, e.g. Token, User")
            @RequestParam(required = false) String entityType,

            @Parameter(description = "Resource UUID filter — must be combined with entityType")
            @RequestParam(required = false) UUID entityId,

            @Parameter(description = "Zero-based page index", schema = @Schema(defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 200)", schema = @Schema(defaultValue = "50"))
            @RequestParam(defaultValue = "50") int size) {

        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        UUID orgId = principal.getOrganizationId();

        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampedSize);

        Page<AuditLogResponse> results;

        if (entityType != null && entityId != null) {
            results = auditLogRepository
                    .findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                            orgId, entityType, entityId, pageable)
                    .map(AuditLogResponse::from);
        } else if (action != null && !action.isBlank()) {
            results = auditLogRepository
                    .findAllByOrganizationIdAndActionOrderByCreatedAtDesc(orgId, action, pageable)
                    .map(AuditLogResponse::from);
        } else {
            results = auditLogRepository
                    .findAllByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                    .map(AuditLogResponse::from);
        }

        return ResponseEntity.ok(results);
    }
}
