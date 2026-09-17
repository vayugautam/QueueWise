package com.queuewise.user.persistence;

import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByOrganizationIdAndPhone(UUID organizationId, String phone);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndPhone(UUID organizationId, String phone);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, UserStatus status);
}
