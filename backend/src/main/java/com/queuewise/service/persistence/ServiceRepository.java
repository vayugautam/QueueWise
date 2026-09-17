package com.queuewise.service.persistence;

import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceDefinition, UUID> {
    List<ServiceDefinition> findAllByOrganizationIdAndStatus(UUID organizationId, ServiceStatus status);
}
