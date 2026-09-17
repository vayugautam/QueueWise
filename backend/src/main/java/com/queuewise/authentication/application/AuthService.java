package com.queuewise.authentication.application;

import com.queuewise.authentication.web.AuthResponse;
import com.queuewise.authentication.web.LoginRequest;
import com.queuewise.authentication.web.RegisterRequest;
import com.queuewise.authentication.web.UserResponse;
import com.queuewise.common.error.ConflictException;
import com.queuewise.common.error.ResourceNotFoundException;
import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.domain.OrganizationStatus;
import com.queuewise.organization.persistence.OrganizationRepository;
import com.queuewise.security.JwtTokenService;
import com.queuewise.security.QueueWiseUserPrincipal;
import com.queuewise.user.domain.Role;
import com.queuewise.user.domain.User;
import com.queuewise.user.persistence.RoleRepository;
import com.queuewise.user.persistence.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.queuewise.audit.domain.AuditEvent;
import com.queuewise.audit.domain.AuditAction;
import java.util.Map;

@Service
public class AuthService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(OrganizationRepository organizationRepository, UserRepository userRepository,
                       RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtTokenService jwtTokenService,
                       ApplicationEventPublisher eventPublisher) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (email == null && phone == null) {
            throw new ConflictException("Email or phone is required.");
        }

        Organization organization = organizationRepository.findByIdAndStatus(
                        request.organizationId(), OrganizationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));

        if (email != null && userRepository.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), email)) {
            throw new ConflictException("An account already exists for the supplied contact.");
        }
        if (phone != null && userRepository.existsByOrganizationIdAndPhone(organization.getId(), phone)) {
            throw new ConflictException("An account already exists for the supplied contact.");
        }

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Required CUSTOMER role is not configured"));
        User user = new User(organization, email, phone, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim());
        user.getRoles().add(customerRole);
        User savedUser = userRepository.save(user);
        QueueWiseUserPrincipal principal = QueueWiseUserPrincipal.from(savedUser);
        JwtTokenService.IssuedToken token = jwtTokenService.issue(principal);
        
        eventPublisher.publishEvent(AuditEvent.success(
            organization.getId(),
            savedUser.getId(),
            AuditAction.USER_REGISTERED,
            "User",
            savedUser.getId(),
            Map.of("registrationMethod", email != null ? "EMAIL" : "PHONE")
        ));
        
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String login = request.login().trim();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.organizationId() + ":" + login, request.password()));
        QueueWiseUserPrincipal principal = (QueueWiseUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByIdAndOrganizationIdAndStatus(
                        principal.getUserId(), principal.getOrganizationId(), com.queuewise.user.domain.UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setLastLoginAt(Instant.now());
        JwtTokenService.IssuedToken token = jwtTokenService.issue(principal);
        
        eventPublisher.publishEvent(AuditEvent.success(
            principal.getOrganizationId(),
            principal.getUserId(),
            AuditAction.LOGIN,
            "User",
            principal.getUserId(),
            Map.of("loginMethod", login.contains("@") ? "EMAIL" : "PHONE")
        ));
        
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
