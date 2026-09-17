package com.queuewise.user.domain;

import com.queuewise.organization.domain.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDomainTest {

    @Test
    void givenNewUser_whenStatusChanges_thenUserBecomesInactiveWithoutChangingIdentity() {
        // Given
        Organization organization = new Organization("Northside Clinic", "northside-clinic");
        User user = new User(organization, "maya@example.com", null, "bcrypt-hash", "Maya", "Patel");

        // When
        user.setStatus(UserStatus.INACTIVE);

        // Then
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.getEmail()).isEqualTo("maya@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void givenRole_whenAdded_thenRoleIsAvailableForAuthorityMapping() {
        // Given
        User user = new User(new Organization("Clinic", "clinic"), null, "+15550001", "hash", "A", "User");

        // When
        user.getRoles().add(new Role("STAFF", "Staff"));

        // Then
        assertThat(user.getRoles()).extracting(Role::getCode).containsExactly("STAFF");
    }
}
