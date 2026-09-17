package com.queuewise.queue.application;

import com.queuewise.appointment.domain.AppointmentStatus;
import com.queuewise.counter.domain.Counter;
import com.queuewise.counter.domain.CounterStatus;
import com.queuewise.service.domain.ServiceDefinition;
import com.queuewise.service.domain.ServiceStatus;
import com.queuewise.token.domain.Token;
import com.queuewise.token.domain.TokenPriority;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;

public final class QueueEnginePolicy {

    public static final Duration MINIMUM_STARVATION_THRESHOLD = Duration.ofMinutes(15);
    public static final int MAX_CANDIDATES = 500;

    private QueueEnginePolicy() { }

    public static boolean eligible(Token token, Counter counter, Instant now) {
        if (counter.getStatus() == CounterStatus.INACTIVE || counter.getStatus() == CounterStatus.PAUSED
                || token.getService().getStatus() != ServiceStatus.ACTIVE
                || token.getStatus() != com.queuewise.token.domain.TokenStatus.WAITING
                || token.getArrivalAt() == null
                || token.getAssignedCounter() != null
                || !token.getQueue().getStatus().name().equals("OPEN")
                || !counter.supports(token.getService())) {
            return false;
        }
        if (token.getAppointment() == null) {
            return true;
        }
        var appointment = token.getAppointment();
        if (!appointment.isPresent()) {
            return false;
        }
        Instant latest = appointment.getScheduledEnd()
                .plus(Duration.ofMinutes(token.getService().getGracePeriodMinutes()));
        return !now.isBefore(appointment.getScheduledStart()) && !now.isAfter(latest);
    }

    public static CandidateScore score(Token token, Instant now) {
        long waitingMinutes = Math.max(0, Duration.between(token.getArrivalAt(), now).toMinutes());
        int aging = (int) Math.min(waitingMinutes, 100);
        int base = token.getPriority() == TokenPriority.URGENT ? 100 : 0;
        boolean onTimeAppointment = token.getAppointment() != null
                && token.getAppointment().getStatus() == AppointmentStatus.CHECKED_IN;
        int appointmentBonus = onTimeAppointment ? 20 : 0;
        int lateAdjustment = token.getAppointment() != null
                && token.getAppointment().getStatus() == AppointmentStatus.LATE ? -20 : 0;
        Duration threshold = starvationThreshold(token.getService());
        boolean protectedNormal = token.getPriority() == TokenPriority.NORMAL
                && !Duration.between(token.getArrivalAt(), now).isNegative()
                && Duration.between(token.getArrivalAt(), now).compareTo(threshold) >= 0;
        return new CandidateScore(token, base + aging + appointmentBonus + lateAdjustment,
                onTimeAppointment, protectedNormal);
    }

    public static Duration starvationThreshold(ServiceDefinition service) {
        Duration byService = Duration.ofSeconds(Math.max(0, service.getEstimatedDurationSeconds()) * 2L);
        return byService.compareTo(MINIMUM_STARVATION_THRESHOLD) > 0
                ? byService : MINIMUM_STARVATION_THRESHOLD;
    }

    public static Comparator<CandidateScore> ordering() {
        return Comparator.comparingInt(CandidateScore::score).reversed()
                .thenComparing(candidate -> candidate.token().getArrivalAt(), Comparator.reverseOrder())
                .thenComparing(CandidateScore::onTimeAppointment, Comparator.reverseOrder())
                .thenComparing(candidate -> Optional.ofNullable(candidate.token().getAppointment())
                        .map(appointment -> appointment.getScheduledStart())
                        .orElse(Instant.MAX), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.token().getId(), Comparator.reverseOrder());
    }

    public static CandidateScore select(List<CandidateScore> candidates) {
        List<CandidateScore> protectedNormals = candidates.stream()
                .filter(CandidateScore::protectedNormal).toList();
        List<CandidateScore> pool = protectedNormals.isEmpty()
                ? candidates.stream().filter(candidate -> candidate.token().getPriority() == TokenPriority.URGENT).toList()
                : protectedNormals;
        if (pool.isEmpty()) pool = candidates;
        return pool.stream().max(ordering()).orElse(null);
    }

    public record CandidateScore(Token token, int score, boolean onTimeAppointment,
                                 boolean protectedNormal) { }
}
