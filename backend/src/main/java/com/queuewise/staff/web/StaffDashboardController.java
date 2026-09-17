package com.queuewise.staff.web;

import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.staff.application.StaffDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@Tag(name = "Staff Dashboard", description = "Staff counter and queue overview")
@SecurityRequirement(name = "BearerAuth")
public class StaffDashboardController {

    private final StaffDashboardService staffDashboardService;

    public StaffDashboardController(StaffDashboardService staffDashboardService) {
        this.staffDashboardService = staffDashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'STAFF', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Get staff dashboard", description = "Returns the authenticated staff member's assigned counters, active work, and organization waiting count. This endpoint is read-only; queue commands remain separate operations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff dashboard returned", content = @Content(schema = @Schema(implementation = StaffDashboardResponse.class), examples = @ExampleObject(value = "{\"waitingTokens\":8,\"counters\":[{\"name\":\"Counter 1\",\"status\":\"AVAILABLE\",\"activeTokenCount\":0}]}"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "Staff role required", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    public ResponseEntity<StaffDashboardResponse> dashboard(Authentication authentication) {
        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(StaffDashboardResponse.from(
                staffDashboardService.getDashboard(principal.getOrganizationId(), principal.getUserId())));
    }

    public record StaffDashboardResponse(UUID organizationId, UUID staffId, long waitingTokens,
                                         List<StaffDashboardService.CounterSnapshot> counters) {
        static StaffDashboardResponse from(StaffDashboardService.StaffDashboardSnapshot snapshot) {
            return new StaffDashboardResponse(snapshot.organizationId(), snapshot.staffId(),
                    snapshot.waitingTokens(), snapshot.counters());
        }
    }
}