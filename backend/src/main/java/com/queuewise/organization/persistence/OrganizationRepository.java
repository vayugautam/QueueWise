package com.queuewise.organization.persistence;

import com.queuewise.organization.domain.Organization;
import com.queuewise.organization.domain.OrganizationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByIdAndStatus(UUID id, OrganizationStatus status);
}
