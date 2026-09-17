package com.queuewise.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.queuewise.authentication.application.AuthService;
import com.queuewise.authentication.web.AuthResponse;
import com.queuewise.authentication.web.RegisterRequest;
import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.domain.OrganizationStatus;
import com.queuewise.organization.persistence.OrganizationRepository;
import com.queuewise.security.JwtTokenService;
import com.queuewise.user.domain.Role;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.RoleRepository;
import com.queuewise.user.persistence.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenService jwtTokenService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AuthService authService;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = new Organization("Northside Clinic", "northside-clinic");
        when(organizationRepository.findByIdAndStatus(any(), org.mockito.ArgumentMatchers.eq(OrganizationStatus.ACTIVE)))
                .thenReturn(Optional.of(organization));
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER", "Customer")));
        when(passwordEncoder.encode("long-enough-password")).thenReturn("bcrypt-hash");
    }

    @Test
    void registerCreatesCustomerResponseWithoutPassword() {
        when(userRepository.existsByOrganizationIdAndEmailIgnoreCase(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            setId(savedUser, UUID.randomUUID());
            setId(organization, organization.getId() == null ? UUID.randomUUID() : organization.getId());
            return savedUser;
        });
        when(jwtTokenService.issue(any())).thenReturn(new JwtTokenService.IssuedToken("jwt", Instant.MAX));

        AuthResponse response = authService.register(new RegisterRequest(
                UUID.randomUUID(), "Maya", "Patel", "maya@example.com", null, "long-enough-password"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().email()).isEqualTo("maya@example.com");
        assertThat(response.user()).hasNoNullFieldsOrPropertiesExcept("phone");
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
