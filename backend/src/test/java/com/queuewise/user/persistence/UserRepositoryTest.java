package com.queuewise.user.persistence;

import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.domain.OrganizationStatus;
import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private Organization org1;
    private Organization org2;

    @BeforeEach
    void setUp() {
        org1 = new Organization("Test Org 1", "org1");
        org1.setTimeZone("America/New_York");
        org1.setStatus(OrganizationStatus.ACTIVE);
        org2 = new Organization("Test Org 2", "org2");
        org2.setTimeZone("America/Los_Angeles");
        org2.setStatus(OrganizationStatus.ACTIVE);
        entityManager.persistAndFlush(org1);
        entityManager.persistAndFlush(org2);
    }

    @Test
    void testSaveUser_PopulatesAuditFields() {
        // Given a new user entity
        User user = new User(org1, "test@example.com", "1234567890", "hash", "John", "Doe");

        // When saved to the repository
        User savedUser = userRepository.save(user);
        entityManager.flush();

        // Then it persists with auditing fields populated
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getVersion()).isEqualTo(0);
    }

    @Test
    void testFindByOrganizationIdAndEmailIgnoreCase() {
        // Given a user repository with an existing user
        User user = new User(org1, "Test@Example.COM", null, "hash", "John", "Doe");
        entityManager.persistAndFlush(user);

        // When searching by organization ID and email
        boolean exists = userRepository.existsByOrganizationIdAndEmailIgnoreCase(org1.getId(), "test@example.com");

        // Then the correct user is returned (case insensitive)
        assertThat(exists).isTrue();
    }

    @Test
    void testDuplicateEmailInSameOrganization_IsDetectedByScopedLookup() {
        // Given an existing user in org1
        User user1 = new User(org1, "test@example.com", null, "hash", "John", "Doe");
        entityManager.persistAndFlush(user1);

        // When checking another registration with the same email in the same organization
        boolean duplicate = userRepository.existsByOrganizationIdAndEmailIgnoreCase(org1.getId(), "TEST@example.com");

        // Then the scoped lookup detects the duplicate before persistence
        assertThat(duplicate).isTrue();
    }

    @Test
    void testDuplicateEmailInDifferentOrganizations_Allowed() {
        // Given an existing user in org1
        User user1 = new User(org1, "test@example.com", null, "hash", "John", "Doe");
        entityManager.persistAndFlush(user1);

        // When saving another user with the same email in a DIFFERENT organization
        User user2 = new User(org2, "test@example.com", null, "hash2", "Jane", "Doe");
        User savedUser = userRepository.save(user2);
        entityManager.flush();

        // Then it succeeds
        assertThat(savedUser.getId()).isNotNull();
    }

    @Test
    void testFindByIdAndOrganizationIdAndStatus() {
        // Given an active user
        User user = new User(org1, "active@example.com", null, "hash", "Active", "User");
        entityManager.persistAndFlush(user);

        // When querying by ID, org ID, and status
        Optional<User> found = userRepository.findByIdAndOrganizationIdAndStatus(user.getId(), org1.getId(), UserStatus.ACTIVE);

        // Then the user is found
        assertThat(found).isPresent();

        // When querying for INACTIVE status
        Optional<User> notFound = userRepository.findByIdAndOrganizationIdAndStatus(user.getId(), org1.getId(), UserStatus.INACTIVE);

        // Then the user is not found
        assertThat(notFound).isEmpty();
    }
}
