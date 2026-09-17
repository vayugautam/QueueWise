package com.queuewise.staff.application;

import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.persistence.CounterRepository;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StaffDashboardService {

    private static final List<TokenStatus> ACTIVE_STATUSES = List.of(TokenStatus.CALLED, TokenStatus.SERVING);

    private final CounterRepository counterRepository;
    private final TokenRepository tokenRepository;

    public StaffDashboardService(CounterRepository counterRepository, TokenRepository tokenRepository) {
        this.counterRepository = counterRepository;
        this.tokenRepository = tokenRepository;
    }

    public StaffDashboardSnapshot getDashboard(UUID organizationId, UUID staffId) {
        List<CounterSnapshot> counters = counterRepository
                .findAllByOrganizationIdAndCurrentStaffIdOrderByNameAsc(organizationId, staffId)
                .stream()
                .map(counter -> new CounterSnapshot(counter.getId(), counter.getName(), counter.getStatus().name(),
                        counter.getPauseReason(), tokenRepository.countByAssignedCounterIdAndStatusIn(
                                counter.getId(), ACTIVE_STATUSES)))
                .toList();
        return new StaffDashboardSnapshot(organizationId, staffId,
                tokenRepository.countByOrganizationIdAndStatus(organizationId, TokenStatus.WAITING), counters);
    }

    public record StaffDashboardSnapshot(UUID organizationId, UUID staffId, long waitingTokens,
                                         List<CounterSnapshot> counters) { }

    public record CounterSnapshot(UUID id, String name, String status, String pauseReason,
                                 long activeTokenCount) { }
}