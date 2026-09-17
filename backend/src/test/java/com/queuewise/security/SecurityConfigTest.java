package com.queuewise.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.web.resources.add-mappings=false")
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/protected-placeholder"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReachProtectedBoundary() throws Exception {
        mockMvc.perform(get("/api/protected-placeholder")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isInternalServerError());
    }
}
