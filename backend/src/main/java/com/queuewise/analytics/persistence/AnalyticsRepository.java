package com.queuewise.analytics.persistence;

import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.CounterPerformanceMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.PeakHourMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.ServiceDemandMetric;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AnalyticsRepository {

    private final JdbcClient jdbcClient;

    public AnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record TokenStats(long totalTokens, long completedTokens, long cancelledTokens, long noShows, Double averageWaitingTimeMinutes, Double averageServiceTimeMinutes) {}

    public TokenStats getAggregateStats(UUID organizationId, Instant startDate, Instant endDate) {
        String sql = """
            SELECT
                COUNT(*) AS total_tokens,
                COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed_tokens,
                COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_tokens,
                COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
                AVG(EXTRACT(EPOCH FROM (called_at - arrival_at))) / 60.0 AS average_waiting_time_minutes,
                AVG(EXTRACT(EPOCH FROM (service_completed_at - service_started_at))) / 60.0 AS average_service_time_minutes
            FROM tokens
            WHERE organization_id = :orgId
              AND created_at >= :startDate
              AND created_at < :endDate
            """;
        
        return jdbcClient.sql(sql)
            .param("orgId", organizationId)
            .param("startDate", startDate)
            .param("endDate", endDate)
            .query((rs, rowNum) -> new TokenStats(
                rs.getLong("total_tokens"),
                rs.getLong("completed_tokens"),
                rs.getLong("cancelled_tokens"),
                rs.getLong("no_shows"),
                rs.getObject("average_waiting_time_minutes") != null ? rs.getDouble("average_waiting_time_minutes") : null,
                rs.getObject("average_service_time_minutes") != null ? rs.getDouble("average_service_time_minutes") : null
            ))
            .single();
    }

    public List<PeakHourMetric> getPeakHours(UUID organizationId, Instant startDate, Instant endDate, String timeZone) {
        String sql = """
            SELECT
                CAST(EXTRACT(HOUR FROM (arrival_at AT TIME ZONE :timeZone)) AS INTEGER) AS hour_of_day,
                COUNT(*) AS token_count
            FROM tokens
            WHERE organization_id = :orgId
              AND arrival_at >= :startDate
              AND arrival_at < :endDate
              AND arrival_at IS NOT NULL
            GROUP BY EXTRACT(HOUR FROM (arrival_at AT TIME ZONE :timeZone))
            ORDER BY token_count DESC, hour_of_day ASC
            LIMIT 10
            """;

        return jdbcClient.sql(sql)
            .param("orgId", organizationId)
            .param("startDate", startDate)
            .param("endDate", endDate)
            .param("timeZone", timeZone)
            .query((rs, rowNum) -> new PeakHourMetric(rs.getInt("hour_of_day"), rs.getLong("token_count")))
            .list();
    }

    public List<ServiceDemandMetric> getServiceDemand(UUID organizationId, Instant startDate, Instant endDate) {
        String sql = """
            SELECT 
                s.name AS service_name,
                COUNT(t.id) AS token_count
            FROM services s
            LEFT JOIN tokens t ON t.service_id = s.id 
                AND t.arrival_at >= :startDate 
                AND t.arrival_at < :endDate
            WHERE s.organization_id = :orgId
            GROUP BY s.id, s.name
            ORDER BY token_count DESC, service_name ASC
            """;

        return jdbcClient.sql(sql)
            .param("orgId", organizationId)
            .param("startDate", startDate)
            .param("endDate", endDate)
            .query((rs, rowNum) -> new ServiceDemandMetric(rs.getString("service_name"), rs.getLong("token_count")))
            .list();
    }

    public List<CounterPerformanceMetric> getCounterPerformance(UUID organizationId, Instant startDate, Instant endDate) {
        String sql = """
            SELECT 
                c.name AS counter_name,
                COUNT(t.id) AS token_count,
                AVG(EXTRACT(EPOCH FROM (t.called_at - t.arrival_at))) / 60.0 AS average_waiting_time_minutes,
                AVG(EXTRACT(EPOCH FROM (t.service_completed_at - t.service_started_at))) / 60.0 AS average_service_time_minutes
            FROM counters c
            LEFT JOIN tokens t ON t.assigned_counter_id = c.id
                AND t.arrival_at >= :startDate 
                AND t.arrival_at < :endDate
                AND t.status = 'COMPLETED'
            WHERE c.organization_id = :orgId
            GROUP BY c.id, c.name
            ORDER BY token_count DESC, counter_name ASC
            """;

        return jdbcClient.sql(sql)
            .param("orgId", organizationId)
            .param("startDate", startDate)
            .param("endDate", endDate)
            .query((rs, rowNum) -> new CounterPerformanceMetric(
                rs.getString("counter_name"),
                rs.getLong("token_count"),
                rs.getObject("average_waiting_time_minutes") != null ? rs.getDouble("average_waiting_time_minutes") : null,
                rs.getObject("average_service_time_minutes") != null ? rs.getDouble("average_service_time_minutes") : null
            ))
            .list();
    }

    public String getOrganizationTimeZone(UUID organizationId) {
        return jdbcClient.sql("SELECT time_zone FROM organizations WHERE id = :orgId")
            .param("orgId", organizationId)
            .query(String.class)
            .single();
    }
}
