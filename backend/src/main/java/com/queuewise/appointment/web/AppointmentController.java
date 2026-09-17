package com.queuewise.appointment.web;

import com.queuewise.appointment.application.AppointmentService;
import com.queuewise.appointment.domain.Appointment;
import com.queuewise.security.QueueWiseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Customer appointment booking")
@SecurityRequirement(name = "BearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Book an appointment", description = "Books a future appointment for the authenticated customer. The service duration determines the end time; organization, service status, queue state, and overlapping active appointments are checked server-side.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment booked", content = @Content(schema = @Schema(implementation = AppointmentResponse.class), examples = @ExampleObject(value = "{\"id\":\"uuid\",\"serviceId\":\"uuid\",\"scheduledStart\":\"2026-09-18T14:00:00Z\",\"scheduledEnd\":\"2026-09-18T14:30:00Z\",\"status\":\"BOOKED\",\"confirmationCode\":\"QW-ABC123DEF456\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "CUSTOMER role required", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Service, customer, or queue not found", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Service unavailable, queue closed, or overlapping appointment", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    public ResponseEntity<AppointmentResponse> book(Authentication authentication,
                                                      @Valid @RequestBody BookAppointmentRequest request) {
        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        Appointment appointment = appointmentService.book(principal.getOrganizationId(), principal.getUserId(),
                request.serviceId(), request.scheduledStart(), Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
    }

    public record BookAppointmentRequest(@NotNull UUID serviceId, @NotNull @Future Instant scheduledStart) { }

    public record AppointmentResponse(UUID id, UUID organizationId, UUID customerId, UUID serviceId,
                                      UUID queueId, Instant scheduledStart, Instant scheduledEnd,
                                      String status, String confirmationCode) {
        static AppointmentResponse from(Appointment appointment) {
            return new AppointmentResponse(appointment.getId(), appointment.getOrganization().getId(),
                    appointment.getCustomer().getId(), appointment.getService().getId(),
                    appointment.getQueue().getId(), appointment.getScheduledStart(), appointment.getScheduledEnd(),
                    appointment.getStatus().name(), appointment.getConfirmationCode());
        }
    }
}