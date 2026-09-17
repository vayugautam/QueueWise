package com.queuewise.counter.domain;

import com.queuewise.organization.domain.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CounterDomainTest {

    @Test
    void givenAvailableCounter_whenPausedAndResumed_thenStateAndReasonFollowLifecycle() {
        // Given
        Counter counter = new Counter(new Organization("Clinic", "clinic"), "Counter 1");

        // When
        counter.pause("Lunch break");
        counter.resume();

        // Then
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.AVAILABLE);
        assertThat(counter.getPauseReason()).isNull();
    }

    @Test
    void givenBlankReason_whenPauseRequested_thenOperationIsRejected() {
        // Given
        Counter counter = new Counter(new Organization("Clinic", "clinic"), "Counter 1");

        // When / Then
        assertThatThrownBy(() -> counter.pause(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.AVAILABLE);
    }
}
