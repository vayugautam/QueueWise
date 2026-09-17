package com.queuewise.authentication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.persistence.OrganizationRepository;
import com.queuewise.user.domain.Role;
import com.queuewise.user.persistence.RoleRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired RoleRepository roleRepository;

    private Organization organization;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[48];
        new SecureRandom().nextBytes(secret);
        registry.add("queuewise.jwt.secret", () -> Base64.getEncoder().encodeToString(secret));
    }

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();
        organizationRepository.deleteAll();
        organization = organizationRepository.save(new Organization("Northside Clinic", "northside-" + System.nanoTime()));
        roleRepository.save(new Role("CUSTOMER", "Customer account"));
    }

    @Test
    void registerThenLoginReturnsJwt() throws Exception {
        String registration = """
                {
                  "organizationId": "%s",
                  "firstName": "Maya",
                  "lastName": "Patel",
                  "email": "maya@example.com",
                  "password": "long-enough-password"
                }
                """.formatted(organization.getId());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));

        String login = """
                {
                  "organizationId": "%s",
                  "login": "maya@example.com",
                  "password": "long-enough-password"
                }
                """.formatted(organization.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
