package com.queuewise.analytics.application;

import com.queuewise.analytics.persistence.AnalyticsRepository;
import com.queuewise.analytics.persistence.AnalyticsRepository.TokenStats;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.CounterPerformanceMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.PeakHourMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.ServiceDemandMetric;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public AnalyticsDashboardResponse getDashboardData(UUID organizationId, Instant startDate, Instant endDate) {
        if (startDate == null) {
            startDate = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = Instant.now();
        }

        TokenStats stats = analyticsRepository.getAggregateStats(organizationId, startDate, endDate);
        String timeZone = analyticsRepository.getOrganizationTimeZone(organizationId);
        List<PeakHourMetric> peakHours = analyticsRepository.getPeakHours(organizationId, startDate, endDate, timeZone);
        List<ServiceDemandMetric> serviceDemand = analyticsRepository.getServiceDemand(organizationId, startDate, endDate);
        List<CounterPerformanceMetric> counterPerformance = analyticsRepository.getCounterPerformance(organizationId, startDate, endDate);

        return new AnalyticsDashboardResponse(
            stats.totalTokens(),
            stats.completedTokens(),
            stats.cancelledTokens(),
            stats.noShows(),
            stats.averageWaitingTimeMinutes(),
            stats.averageServiceTimeMinutes(),
            peakHours,
            serviceDemand,
            counterPerformance
        );
    }
}
