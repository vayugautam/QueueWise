package com.queuewise.authentication.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LoginRequest(
        @NotNull UUID organizationId,
        @NotBlank String login,
        @NotBlank String password) {
}
