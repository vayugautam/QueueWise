package com.queuewise.analytics.web;

import com.queuewise.analytics.application.AnalyticsService;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse;
import com.queuewise.security.QueueWiseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for analytics and reporting data.
 *
 * <p><strong>Base path note:</strong> This controller uses {@code /api/v1/analytics}
 * while most other controllers use {@code /api/...}. This is a pre-existing
 * inconsistency tracked as tech debt (see OpenApiConfig for full explanation).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Operational analytics and reporting dashboard")
@SecurityRequirement(name = "BearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Returns the analytics dashboard for the caller's organisation.
     *
     * <p>Includes aggregate token statistics, peak-hour distribution, service demand,
     * and counter performance for the requested date range. Defaults to the last 30 days
     * when no range is specified.
     *
     * @param authentication injected Spring Security principal
     * @param startDate      optional range start (ISO-8601 instant)
     * @param endDate        optional range end (ISO-8601 instant)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(
        summary = "Get analytics dashboard",
        description = """
            Returns aggregate token statistics, peak-hour breakdown, service demand, and
            counter performance for the caller's organisation.
            
            **Date range**: if omitted, defaults to the past 30 days.
            
            **Roles required**: ADMIN or SUPERVISOR.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard data returned"),
        @ApiResponse(responseCode = "400", description = "Invalid date format",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @ApiResponse(responseCode = "403", description = "Insufficient role — ADMIN or SUPERVISOR required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    public ResponseEntity<AnalyticsDashboardResponse> getDashboard(
            Authentication authentication,

            @Parameter(description = "Range start (ISO-8601 instant, e.g. 2026-09-01T00:00:00Z)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,

            @Parameter(description = "Range end (ISO-8601 instant, e.g. 2026-09-17T23:59:59Z)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        UUID orgId = principal.getOrganizationId();
        return ResponseEntity.ok(analyticsService.getDashboardData(orgId, startDate, endDate));
    }
}
