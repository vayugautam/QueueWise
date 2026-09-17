package com.queuewise.queue.persistence;

import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface QueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByOrganizationIdAndServiceIdAndBusinessDate(UUID organizationId, UUID serviceId, LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Queue> findWithLockByIdAndStatus(UUID id, QueueStatus status);
}
