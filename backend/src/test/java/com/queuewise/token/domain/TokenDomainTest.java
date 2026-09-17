package com.queuewise.token.domain;

import com.queuewise.counter.domain.Counter;
import com.queuewise.organization.domain.Organization;
import com.queuewise.queue.domain.Queue;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TokenDomainTest {

    @Test
    void givenWaitingToken_whenCalledStartedAndCompleted_thenLifecycleTimestampsAreRecorded() {
        // Given
        Organization organization = new Organization("Clinic", "clinic");
        Queue queue = mock(Queue.class);
        ServiceDefinition service = new ServiceDefinition(organization, "Consultation", 600);
        User customer = new User(organization, "customer@example.com", null, "hash", "Customer", "One");
        Token token = new Token(organization, queue, service, customer, null, 1, "T-0001",
                TokenSource.WALK_IN, TokenPriority.NORMAL, Instant.parse("2026-09-17T10:00:00Z"));
        Counter counter = new Counter(organization, "Counter 1");
        Instant calledAt = Instant.parse("2026-09-17T10:05:00Z");
        Instant startedAt = Instant.parse("2026-09-17T10:06:00Z");
        Instant completedAt = Instant.parse("2026-09-17T10:16:00Z");

        // When
        token.call(counter, calledAt);
        token.startService(startedAt);
        token.complete(completedAt);

        // Then
        assertThat(token.getStatus()).isEqualTo(TokenStatus.COMPLETED);
        assertThat(token.getAssignedCounter()).isSameAs(counter);
        assertThat(token.getCalledAt()).isEqualTo(calledAt);
        assertThat(token.getServiceStartedAt()).isEqualTo(startedAt);
        assertThat(token.getServiceCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void givenCalledToken_whenCancelled_thenReasonAndTerminalStatusAreStored() {
        // Given
        Token token = new Token(new Organization("Clinic", "clinic"), mock(Queue.class),
                mock(ServiceDefinition.class), mock(User.class), null, 1, "T-0001",
                TokenSource.WALK_IN, TokenPriority.NORMAL, Instant.now());

        // When
        token.cancel("Customer left");

        // Then
        assertThat(token.getStatus()).isEqualTo(TokenStatus.CANCELLED);
        assertThat(token.getTerminalReason()).isEqualTo("Customer left");
        assertThat(token.getServiceCompletedAt()).isNull();
    }
}
