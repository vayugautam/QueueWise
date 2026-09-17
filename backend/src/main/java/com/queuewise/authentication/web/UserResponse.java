package com.queuewise.authentication.web;

import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.user.domain.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(UUID id, UUID organizationId, String firstName, String lastName,
                           String email, String phone, Set<String> roles) {
        public static UserResponse from(User user) {
                return new UserResponse(
                                user.getId(),
                                user.getOrganization().getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getEmail(),
                                user.getPhone(),
                                user.getRoles().stream()
                                                .map(role -> role.getCode())
                                                .collect(Collectors.toUnmodifiableSet()));
        }

    public static UserResponse from(QueueWiseUserPrincipal principal, String firstName,
                                    String lastName, String email, String phone) {
        return new UserResponse(
                principal.getUserId(),
                principal.getOrganizationId(),
                firstName,
                lastName,
                email,
                phone,
                principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
