package com.queuewise.staff.application;

import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import com.queuewise.counter.persistence.CounterRepository;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffDashboardServiceTest {

    @Mock CounterRepository counterRepository;
    @Mock TokenRepository tokenRepository;

    @Test
    void givenAssignedCounterAndWaitingWork_whenDashboardRequested_thenReadModelContainsOperationalCounts() {
        // Given
        UUID organizationId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID counterId = UUID.randomUUID();
        Counter counter = org.mockito.Mockito.mock(Counter.class);
        when(counter.getId()).thenReturn(counterId);
        when(counter.getName()).thenReturn("Counter 1");
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counterRepository.findAllByOrganizationIdAndCurrentStaffIdOrderByNameAsc(organizationId, staffId))
                .thenReturn(List.of(counter));
        when(tokenRepository.countByOrganizationIdAndStatus(organizationId, TokenStatus.WAITING)).thenReturn(4L);
        when(tokenRepository.countByAssignedCounterIdAndStatusIn(org.mockito.ArgumentMatchers.eq(counterId), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(1L);

        // When
        StaffDashboardService.StaffDashboardSnapshot dashboard =
                new StaffDashboardService(counterRepository, tokenRepository).getDashboard(organizationId, staffId);

        // Then
        assertThat(dashboard.waitingTokens()).isEqualTo(4);
        assertThat(dashboard.counters()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.name()).isEqualTo("Counter 1");
            assertThat(snapshot.status()).isEqualTo("AVAILABLE");
            assertThat(snapshot.activeTokenCount()).isEqualTo(1);
        });
    }
}
