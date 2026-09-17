package com.queuewise.service.domain;

import com.queuewise.organization.domain.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDefinitionTest {

    @Test
    void givenValidService_whenCreated_thenOperationalDefaultsAreApplied() {
        // Given
        Organization organization = new Organization("Northside Clinic", "northside-clinic");

        // When
        ServiceDefinition service = new ServiceDefinition(organization, "Consultation", 600);

        // Then
        assertThat(service.getOrganization()).isSameAs(organization);
        assertThat(service.getName()).isEqualTo("Consultation");
        assertThat(service.getEstimatedDurationSeconds()).isEqualTo(600);
        assertThat(service.getStatus()).isEqualTo(ServiceStatus.ACTIVE);
        assertThat(service.getSlotCapacity()).isEqualTo((short) 1);
    }
}
