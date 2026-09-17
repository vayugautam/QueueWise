package com.queuewise.counter.persistence;

import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface CounterRepository extends JpaRepository<Counter, UUID> {
    List<Counter> findAllByOrganizationIdAndStatus(UUID organizationId, CounterStatus status);
    List<Counter> findAllByOrganizationIdAndCurrentStaffIdOrderByNameAsc(UUID organizationId, UUID staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Counter> findWithLockById(UUID id);
}
