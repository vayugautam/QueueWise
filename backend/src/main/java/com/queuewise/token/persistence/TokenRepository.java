package com.queuewise.token.persistence;

import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface TokenRepository extends JpaRepository<Token, UUID> {
    List<Token> findAllByQueueIdAndStatusOrderByArrivalAtAscId(UUID queueId, TokenStatus status);
    List<Token> findAllByQueueIdAndStatusInOrderByArrivalAtAscId(UUID queueId, List<TokenStatus> statuses);
    List<Token> findAllByOrganizationIdAndCustomerIdOrderByCreatedAtDesc(UUID organizationId, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Token> findWithLockById(UUID id);

    List<Token> findTop500ByOrganizationIdAndStatusOrderByArrivalAtAscId(UUID organizationId, TokenStatus status);

    boolean existsByAssignedCounterIdAndStatusIn(UUID counterId, List<TokenStatus> statuses);

    boolean existsByAppointmentId(UUID appointmentId);

    long countByOrganizationIdAndStatus(UUID organizationId, TokenStatus status);
    long countByAssignedCounterIdAndStatusIn(UUID counterId, List<TokenStatus> statuses);
}
