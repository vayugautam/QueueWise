package com.queuewise.service.persistence;

import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.domain.OrganizationStatus;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.hibernate.exception.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
public class ServiceDefinitionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ServiceRepository serviceRepository;

    private Organization org;

    @BeforeEach
    void setUp() {
        org = new Organization("Test Org", "org-test");
        org.setTimeZone("America/New_York");
        org.setStatus(OrganizationStatus.ACTIVE);
        entityManager.persistAndFlush(org);
    }

    @Test
    void testSaveServiceDefinition_PopulatesAuditFields() {
        // Given a valid service definition
        ServiceDefinition service = new ServiceDefinition(org, "Consultation", 1800);

        // When saved
        ServiceDefinition savedService = serviceRepository.save(service);
        entityManager.flush();

        // Then it persists successfully with auditing fields
        assertThat(savedService.getId()).isNotNull();
        assertThat(savedService.getStatus()).isEqualTo(ServiceStatus.ACTIVE);
        assertThat(savedService.getVersion()).isEqualTo(0);
        assertThat(savedService.getGracePeriodMinutes()).isEqualTo((short) 15); // default
    }

    @Test
    void testSaveServiceDefinition_MissingName_ThrowsException() {
        // Given a service definition missing a name
        ServiceDefinition service = new ServiceDefinition(org, null, 1800);

        // When/Then saving throws DataIntegrityViolationException due to nullable=false
        assertThrows(ConstraintViolationException.class, () -> {
            serviceRepository.save(service);
            entityManager.flush();
        });
    }
}
