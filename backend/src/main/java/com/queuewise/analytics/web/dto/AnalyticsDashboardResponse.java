package com.queuewise.analytics.web.dto;

import java.util.List;

public record AnalyticsDashboardResponse(
    long totalTokens,
    long completedTokens,
    long cancelledTokens,
    long noShows,
    Double averageWaitingTimeMinutes,
    Double averageServiceTimeMinutes,
    List<PeakHourMetric> peakHours,
    List<ServiceDemandMetric> serviceDemand,
    List<CounterPerformanceMetric> counterPerformance
) {
    public record PeakHourMetric(int hourOfDay, long tokenCount) {}
    public record ServiceDemandMetric(String serviceName, long tokenCount) {}
    public record CounterPerformanceMetric(
        String counterName, 
        long tokenCount, 
        Double averageWaitingTimeMinutes, 
        Double averageServiceTimeMinutes
    ) {}
}
