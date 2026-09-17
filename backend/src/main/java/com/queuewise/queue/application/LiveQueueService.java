package com.queuewise.queue.application;

import com.queuewise.common.error.ConflictException;
import com.queuewise.common.error.ResourceNotFoundException;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LiveQueueService {

    private static final List<TokenStatus> VISIBLE_STATUSES =
            List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING);

    private final QueueRepository queueRepository;
    private final TokenRepository tokenRepository;

    public LiveQueueService(QueueRepository queueRepository, TokenRepository tokenRepository) {
        this.queueRepository = queueRepository;
        this.tokenRepository = tokenRepository;
    }

    public LiveQueueSnapshot getSnapshot(UUID organizationId, UUID queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found."));
        if (!queue.getOrganization().getId().equals(organizationId)) {
            throw new ConflictException("Organization scope mismatch.");
        }

        List<TokenView> tokens = new java.util.ArrayList<>();
        int waitingPosition = 0;
        int estimatedWaitMinutes = 0;
        int serviceMinutes = Math.max(1, (int) Math.ceil(queue.getService().getEstimatedDurationSeconds() / 60.0));
        for (Token token : tokenRepository.findAllByQueueIdAndStatusInOrderByArrivalAtAscId(queueId, VISIBLE_STATUSES)) {
            Integer position = null;
            Integer tokenWait = null;
            if (token.getStatus() == TokenStatus.WAITING) {
                position = ++waitingPosition;
                estimatedWaitMinutes = position * serviceMinutes;
                tokenWait = estimatedWaitMinutes;
            }
            tokens.add(new TokenView(token.getId(), token.getDisplayCode(), token.getTokenNumber(),
                    token.getPriority().name(), token.getStatus().name(), position, tokenWait,
                    token.getArrivalAt()));
        }
        return new LiveQueueSnapshot(queue.getId(), queue.getService().getId(), queue.getBusinessDate(),
                queue.getStatus().name(), waitingPosition, estimatedWaitMinutes, Instant.now(), tokens);
    }

    public record LiveQueueSnapshot(UUID queueId, UUID serviceId, java.time.LocalDate businessDate,
                                    String queueStatus, int waitingCount, int estimatedWaitMinutes,
                                    Instant observedAt, List<TokenView> tokens) { }

    public record TokenView(UUID id, String displayCode, int tokenNumber, String priority, String status,
                            Integer position, Integer estimatedWaitMinutes, Instant arrivalAt) { }
}