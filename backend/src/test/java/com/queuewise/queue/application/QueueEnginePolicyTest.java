package com.queuewise.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.queuewise.appointment.domain.Appointment;
import com.queuewise.appointment.domain.AppointmentStatus;
import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import com.queuewise.queue.domain.Queue;
import com.queuewise.queue.domain.QueueStatus;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenPriority;
import com.queuewise.token.domain.TokenStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueEnginePolicyTest {

    private final Instant now = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void urgentWinsDuringNormalOperation() {
        Token normal = token(TokenPriority.NORMAL, now.minusSeconds(300), UUID.randomUUID());
        Token urgent = token(TokenPriority.URGENT, now.minusSeconds(60), UUID.randomUUID());

        QueueEnginePolicy.CandidateScore selected = QueueEnginePolicy.select(List.of(
                QueueEnginePolicy.score(normal, now), QueueEnginePolicy.score(urgent, now)));

        assertThat(selected.token()).isSameAs(urgent);
    }

    @Test
    void protectedNormalWinsOverNewUrgentWork() {
        Token normal = token(TokenPriority.NORMAL, now.minus(DurationMinutes.of(16)), UUID.randomUUID());
        Token urgent = token(TokenPriority.URGENT, now.minusSeconds(30), UUID.randomUUID());

        QueueEnginePolicy.CandidateScore selected = QueueEnginePolicy.select(List.of(
                QueueEnginePolicy.score(normal, now), QueueEnginePolicy.score(urgent, now)));

        assertThat(selected.token()).isSameAs(normal);
    }

    @Test
    void appointmentAndUuidTieBreaksAreDeterministic() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Token first = token(TokenPriority.NORMAL, now.minusSeconds(60), firstId);
        Token second = token(TokenPriority.NORMAL, now.minusSeconds(60), secondId);

        assertThat(QueueEnginePolicy.select(List.of(
                QueueEnginePolicy.score(second, now), QueueEnginePolicy.score(first, now))).token())
                .isSameAs(first);
    }

    @Test
    void bookedAppointmentAndIncompatibleOrPausedCounterAreExcluded() {
        Token appointmentToken = token(TokenPriority.NORMAL, now, UUID.randomUUID());
        Appointment appointment = mock(Appointment.class);
        when(appointment.isPresent()).thenReturn(false);
        when(appointmentToken.getAppointment()).thenReturn(appointment);
        Counter counter = mock(Counter.class);
        when(counter.getStatus()).thenReturn(CounterStatus.AVAILABLE);
        when(counter.supports(appointmentToken.getService())).thenReturn(false);

        assertThat(QueueEnginePolicy.eligible(appointmentToken, counter, now)).isFalse();

        when(appointmentToken.getAppointment()).thenReturn(null);
        when(counter.supports(appointmentToken.getService())).thenReturn(true);
        when(counter.getStatus()).thenReturn(CounterStatus.PAUSED);
        assertThat(QueueEnginePolicy.eligible(appointmentToken, counter, now)).isFalse();
    }

    private Token token(TokenPriority priority, Instant arrival, UUID id) {
        Token token = mock(Token.class);
        ServiceDefinition service = mock(ServiceDefinition.class);
        Queue queue = mock(Queue.class);
        when(token.getId()).thenReturn(id);
        when(token.getPriority()).thenReturn(priority);
        when(token.getArrivalAt()).thenReturn(arrival);
        when(token.getStatus()).thenReturn(TokenStatus.WAITING);
        when(token.getService()).thenReturn(service);
        when(token.getQueue()).thenReturn(queue);
        when(queue.getStatus()).thenReturn(QueueStatus.OPEN);
        when(service.getEstimatedDurationSeconds()).thenReturn(300);
        when(service.getGracePeriodMinutes()).thenReturn((short) 15);
        return token;
    }

    private static final class DurationMinutes {
        private DurationMinutes() { }
        static java.time.Duration of(int minutes) { return java.time.Duration.ofMinutes(minutes); }
    }
}