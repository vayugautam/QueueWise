package com.queuewise.queue.web;

import com.queuewise.queue.application.LiveQueueService;
import com.queuewise.security.QueueWiseUserPrincipal;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
@Tag(name = "Live Queue", description = "Privacy-safe queue snapshots")
@SecurityRequirement(name = "BearerAuth")
public class QueueController {

    private final LiveQueueService liveQueueService;

    public QueueController(LiveQueueService liveQueueService) {
        this.liveQueueService = liveQueueService;
    }

    @GetMapping("/{queueId}/live")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a live queue snapshot", description = "Returns a read-only organization-scoped snapshot of waiting and active tokens. The response contains display codes and positions only; it never exposes customer identity or contact data. Requires any authenticated QueueWise role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live queue returned", content = @Content(schema = @Schema(implementation = LiveQueueResponse.class), examples = @ExampleObject(value = "{\"queueId\":\"uuid\",\"queueStatus\":\"OPEN\",\"waitingCount\":3,\"estimatedWaitMinutes\":24,\"tokens\":[{\"displayCode\":\"T-0007\",\"status\":\"WAITING\",\"position\":1}]}"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Queue not found", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Queue belongs to another organization", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    public ResponseEntity<LiveQueueResponse> live(@PathVariable UUID queueId, Authentication authentication) {
        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(LiveQueueResponse.from(
                liveQueueService.getSnapshot(principal.getOrganizationId(), queueId)));
    }

    public record LiveQueueResponse(UUID queueId, UUID serviceId, LocalDate businessDate, String queueStatus,
                                    int waitingCount, int estimatedWaitMinutes, Instant observedAt,
                                    List<LiveQueueService.TokenView> tokens) {
        static LiveQueueResponse from(LiveQueueService.LiveQueueSnapshot snapshot) {
            return new LiveQueueResponse(snapshot.queueId(), snapshot.serviceId(), snapshot.businessDate(),
                    snapshot.queueStatus(), snapshot.waitingCount(), snapshot.estimatedWaitMinutes(),
                    snapshot.observedAt(), snapshot.tokens());
        }
    }
}