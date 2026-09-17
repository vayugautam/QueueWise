package com.queuewise.queue.application;

import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenPriority;
import com.queuewise.token.domain.TokenStatus;
import com.queuewise.token.persistence.TokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveQueueServiceTest {

    @Mock QueueRepository queueRepository;
    @Mock TokenRepository tokenRepository;

    @Test
    void givenWaitingTokens_whenSnapshotRequested_thenPositionsAndEstimatesAreCalculatedWithoutCustomerData() {
        // Given
        UUID organizationId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        ServiceDefinition service = org.mockito.Mockito.mock(ServiceDefinition.class);
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        Token first = token("T-0001", TokenStatus.WAITING);
        Token second = token("T-0002", TokenStatus.WAITING);
        when(organization.getId()).thenReturn(organizationId);
        when(service.getId()).thenReturn(UUID.randomUUID());
        when(service.getEstimatedDurationSeconds()).thenReturn(600);
        when(queue.getId()).thenReturn(queueId);
        when(queue.getOrganization()).thenReturn(organization);
        when(queue.getService()).thenReturn(service);
        when(queue.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 17));
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);
        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
        when(tokenRepository.findAllByQueueIdAndStatusInOrderByArrivalAtAscId(queueId,
                List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING)))
                .thenReturn(List.of(first, second));

        // When
        LiveQueueService.LiveQueueSnapshot snapshot =
                new LiveQueueService(queueRepository, tokenRepository).getSnapshot(organizationId, queueId);

        // Then
        assertThat(snapshot.waitingCount()).isEqualTo(2);
        assertThat(snapshot.estimatedWaitMinutes()).isEqualTo(20);
        assertThat(snapshot.tokens()).extracting(LiveQueueService.TokenView::position)
                .containsExactly(1, 2);
        assertThat(snapshot.tokens().get(0).estimatedWaitMinutes()).isEqualTo(10);
    }

    @Test
    void givenQueueFromAnotherOrganization_whenSnapshotRequested_thenAccessIsRejected() {
        // Given
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        Queue queue = org.mockito.Mockito.mock(Queue.class);
        when(organization.getId()).thenReturn(otherOrganizationId);
        when(queue.getOrganization()).thenReturn(organization);
        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new LiveQueueService(queueRepository, tokenRepository).getSnapshot(organizationId, queueId))
                .isInstanceOf(com.queuewise.common.error.ConflictException.class);
    }

    private Token token(String displayCode, TokenStatus status) {
        Token token = org.mockito.Mockito.mock(Token.class);
        when(token.getId()).thenReturn(UUID.randomUUID());
        when(token.getDisplayCode()).thenReturn(displayCode);
        when(token.getTokenNumber()).thenReturn(Integer.parseInt(displayCode.substring(2)));
        when(token.getPriority()).thenReturn(TokenPriority.NORMAL);
        when(token.getStatus()).thenReturn(status);
        when(token.getArrivalAt()).thenReturn(Instant.parse("2026-09-17T10:00:00Z"));
        return token;
    }
}
