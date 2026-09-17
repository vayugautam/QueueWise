package com.queuewise.analytics.persistence;

import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.CounterPerformanceMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.PeakHourMetric;
import com.queuewise.analytics.web.dto.AnalyticsDashboardResponse.ServiceDemandMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AnalyticsRepository.class)
public class AnalyticsRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AnalyticsRepository repository;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        
        // Setup schema
        jdbcClient.sql("""
            CREATE TABLE IF NOT EXISTS organizations (id UUID PRIMARY KEY, name VARCHAR(255), time_zone VARCHAR(64) DEFAULT 'UTC');
            CREATE TABLE IF NOT EXISTS services (id UUID PRIMARY KEY, organization_id UUID, name VARCHAR(255));
            CREATE TABLE IF NOT EXISTS counters (id UUID PRIMARY KEY, organization_id UUID, name VARCHAR(255));
            CREATE TABLE IF NOT EXISTS tokens (
                id UUID PRIMARY KEY, 
                organization_id UUID, 
                service_id UUID,
                assigned_counter_id UUID,
                status VARCHAR(50),
                arrival_at TIMESTAMP,
                called_at TIMESTAMP,
                service_started_at TIMESTAMP,
                service_completed_at TIMESTAMP,
                created_at TIMESTAMP
            );
            
            TRUNCATE TABLE tokens;
            TRUNCATE TABLE services;
            TRUNCATE TABLE counters;
            TRUNCATE TABLE organizations;
        """).update();

        // Insert baseline data
        jdbcClient.sql("INSERT INTO organizations (id, name, time_zone) VALUES (?, 'Test Org', 'America/New_York')").param(orgId).update();

        UUID serviceId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO services (id, organization_id, name) VALUES (?, ?, 'Consultation')")
                .param(serviceId).param(orgId).update();

        UUID counterId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO counters (id, organization_id, name) VALUES (?, ?, 'Counter 1')")
                .param(counterId).param(orgId).update();

        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        
        // Insert a completed token (Wait time: 5 mins, Service time: 10 mins)
        jdbcClient.sql("""
            INSERT INTO tokens (id, organization_id, service_id, assigned_counter_id, status, arrival_at, called_at, service_started_at, service_completed_at, created_at)
            VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?)
        """)
        .param(UUID.randomUUID()).param(orgId).param(serviceId).param(counterId)
        .param(oneHourAgo)
        .param(oneHourAgo.plus(5, ChronoUnit.MINUTES))
        .param(oneHourAgo.plus(5, ChronoUnit.MINUTES))
        .param(oneHourAgo.plus(15, ChronoUnit.MINUTES))
        .param(oneHourAgo)
        .update();

        // Insert a cancelled token
        jdbcClient.sql("""
            INSERT INTO tokens (id, organization_id, service_id, assigned_counter_id, status, arrival_at, created_at)
            VALUES (?, ?, ?, ?, 'CANCELLED', ?, ?)
        """)
        .param(UUID.randomUUID()).param(orgId).param(serviceId).param(null)
        .param(now.minus(2, ChronoUnit.HOURS))
        .param(now.minus(2, ChronoUnit.HOURS))
        .update();
    }

    @Test
    void testGetAggregateStats() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(1, ChronoUnit.DAYS);

        AnalyticsRepository.TokenStats stats = repository.getAggregateStats(orgId, startDate, endDate);

        assertThat(stats.totalTokens()).isEqualTo(2);
        assertThat(stats.completedTokens()).isEqualTo(1);
        assertThat(stats.cancelledTokens()).isEqualTo(1);
        assertThat(stats.noShows()).isEqualTo(0);
        // Wait time: 5 minutes
        assertThat(stats.averageWaitingTimeMinutes()).isEqualTo(5.0);
        // Service time: 10 minutes
        assertThat(stats.averageServiceTimeMinutes()).isEqualTo(10.0);
    }

    @Test
    void aggregateStats_excludesTokenCreatedAtEndBoundary_andCountsNoShows() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant inRange = Instant.now();

        jdbcClient.sql("""
            INSERT INTO tokens (id, organization_id, service_id, status, arrival_at, created_at)
            SELECT ?, organization_id, service_id, 'NO_SHOW', ?, ? FROM tokens LIMIT 1
            """)
            .param(UUID.randomUUID())
            .param(inRange)
            .param(inRange)
            .update();

        jdbcClient.sql("""
            INSERT INTO tokens (id, organization_id, service_id, status, arrival_at, created_at)
            SELECT ?, organization_id, service_id, 'NO_SHOW', ?, ? FROM tokens LIMIT 1
            """)
            .param(UUID.randomUUID())
            .param(endDate)
            .param(endDate)
            .update();

        AnalyticsRepository.TokenStats stats = repository.getAggregateStats(orgId, startDate, endDate);

        assertThat(stats.noShows()).isEqualTo(1);
        assertThat(stats.totalTokens()).isEqualTo(3);
    }

    @Test
    void testGetPeakHours() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(1, ChronoUnit.DAYS);

        List<PeakHourMetric> peakHours = repository.getPeakHours(orgId, startDate, endDate, "America/New_York");
        assertThat(peakHours).isNotEmpty();
    }

    @Test
    void testGetServiceDemand() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(1, ChronoUnit.DAYS);

        List<ServiceDemandMetric> demand = repository.getServiceDemand(orgId, startDate, endDate);
        assertThat(demand).hasSize(1);
        assertThat(demand.get(0).serviceName()).isEqualTo("Consultation");
        assertThat(demand.get(0).tokenCount()).isEqualTo(2); // One completed, one cancelled
    }

    @Test
    void testGetCounterPerformance() {
        Instant startDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(1, ChronoUnit.DAYS);

        List<CounterPerformanceMetric> perf = repository.getCounterPerformance(orgId, startDate, endDate);
        assertThat(perf).hasSize(1);
        assertThat(perf.get(0).counterName()).isEqualTo("Counter 1");
        assertThat(perf.get(0).tokenCount()).isEqualTo(1); // Only the completed one has a counter
        assertThat(perf.get(0).averageWaitingTimeMinutes()).isEqualTo(5.0);
        assertThat(perf.get(0).averageServiceTimeMinutes()).isEqualTo(10.0);
    }
}
