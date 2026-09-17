package com.queuewise.authentication.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Email @Size(max = 254) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(min = 12, max = 128) String password) {
}
