# QueueWise Performance Plan

**Version:** 1.0  
**Source of truth:** [TRD.md](TRD.md), [DATABASE_DESIGN.md](DATABASE_DESIGN.md), and [QUEUE_ENGINE.md](QUEUE_ENGINE.md)

## 1. Performance Objectives

Initial production targets from the TRD:

- 95% of common token, queue, and service-list reads complete within 2 seconds under normal load.
- 95% of committed queue events reach active views within 5 seconds.
- Core booking and staff actions remain responsive under expected clinic/office counter load.
- No correctness compromise for latency: queue and appointment decisions remain transactional.

Measure p50, p95, p99, throughput, error rate, database time, lock wait time, WebSocket latency, and resource saturation. Do not optimize based only on average response time.

## 2. Performance Model

The modular monolith has four primary resource pools:

1. HTTP request threads.
2. Database connections and PostgreSQL CPU/IO.
3. WebSocket sessions and outbound message processing.
4. Notification/provider work.

Queue selection and appointment booking are database-coordination workloads. Analytics is read-heavy. WebSocket delivery is connection-heavy but should not participate in business transactions.

## 3. API Latency

### Bottleneck: slow application endpoints

**Why it occurs:** Controller-to-service work may include multiple repository calls, authorization checks, mapping, database locks, or provider calls. Large DTOs and unbounded lists also increase serialization time.

**How to measure:** Add request duration metrics by endpoint, status, organization-safe route template, and outcome. Use Spring observations/Micrometer, structured correlation logs, and p95/p99 dashboards. Trace database time separately from application time.

**Mitigation:** Keep controllers thin; use DTO projections; validate before expensive work; paginate; avoid external providers inside transactions; set query and request timeouts; use bounded payloads.

**Trade-off:** More instrumentation and projection code increase maintenance. Explicit limits may require frontend pagination.

### Bottleneck: slow mutation from lock waits

**Why it occurs:** Final-slot booking, token numbering, call-next, and counter ownership intentionally serialize scarce resources.

**How to measure:** Track transaction duration, PostgreSQL lock wait time, deadlocks, serialization retries, conflict rate, and queue size at the time of command.

**Mitigation:** Lock the narrowest row; acquire locks in consistent order; keep transactions short; do not call email/SMS/WebSocket inside them; use bounded retries; shard contention by service/date/queue where the domain allows.

**Trade-off:** More precise locking and retries make behavior more complex, but removing locks would corrupt business state.

## 4. Database Queries

### Bottleneck: missing or ineffective indexes

**Why it occurs:** Queue, availability, history, audit, and analytics queries filter by organization, service, status, and time. Without matching indexes PostgreSQL scans large tables.

**How to measure:** Use `EXPLAIN (ANALYZE, BUFFERS)` on representative queries, PostgreSQL slow-query logs, `pg_stat_statements`, and index usage statistics.

**Mitigation:** Maintain indexes for organization scope, token queue/status/priority/arrival, appointment service/time/status, event type/time, counter sessions, and audit timestamp. Keep predicates aligned with index leading columns. Use partial indexes for active statuses where appropriate.

**Trade-off:** Indexes consume storage and slow inserts/updates. Add only indexes supported by query plans and measured workload.

### Bottleneck: inefficient JPA behavior

**Why it occurs:** Lazy associations accessed in loops create N+1 queries; eager graphs create oversized joins and duplicate rows.

**How to measure:** Hibernate SQL logging in tests, datasource query counters, p6spy-like development tooling if approved, and query count assertions for critical service methods.

**Mitigation:** Use DTO projections, explicit fetch joins/entity graphs, batch size only when justified, and separate detail/list queries. Keep entities out of API serialization.

**Trade-off:** More explicit repository methods and projections reduce generality but make query cost visible.

### Bottleneck: connection pool exhaustion

**Why it occurs:** Transactions wait too long, analytics queries hold connections, or pool size is larger than PostgreSQL can support.

**How to measure:** Hikari active/idle/pending connection metrics, transaction duration, PostgreSQL connection count, and timeout errors.

**Mitigation:** Bound pool size; set connection, statement, and transaction timeouts; keep analytics separate in read-only transactions; fix slow queries before increasing pool size; size pool based on database CPU rather than HTTP thread count.

**Trade-off:** A small pool causes queueing under spikes but protects PostgreSQL. A large pool can increase context switching and database contention.

## 5. Queue Lookup

### Bottleneck: scanning all waiting tokens

**Why it occurs:** The queue engine filters status, service, counter compatibility, appointment readiness, priority, and arrival age. A naive query loads an entire organization or sorts every historical token.

**How to measure:** Candidate-query duration, rows examined versus returned, `EXPLAIN BUFFERS`, lock wait, candidate count, and call-next p95.

**Mitigation:** Query only the relevant queue/business date and active `WAITING` tokens. Join `counter_services`. Index `(queue_id, status, priority, arrival_at, id)` or the measured equivalent. Apply a bounded candidate page, calculate fairness score deterministically, lock/recheck only the winner. Keep terminal tokens out of active indexes with partial indexes where appropriate.

**Trade-off:** Application-side score calculation may require a bounded candidate set and careful fairness proof. Pushing every policy expression into SQL may be faster but harder to test and maintain.

### Bottleneck: lock contention in call-next

**Why it occurs:** Many staff request the same queue/counter simultaneously.

**How to measure:** PostgreSQL lock wait events, call-next conflict/retry count, counter throughput, and time from request to assignment.

**Mitigation:** Lock the counter first, use `SKIP LOCKED` for unrelated candidate rows where safe, lock only the selected token, and partition queues by service/date. Do not hold locks while publishing messages.

**Trade-off:** `SKIP LOCKED` can make selection less obvious and must be followed by state rechecks. Fairness correctness remains mandatory.

## 6. Analytics Queries

### Bottleneck: large aggregate scans

**Why it occurs:** Metrics combine tokens, token events, appointments, counter sessions, and time-zone grouping over retained history.

**How to measure:** Query plans, duration by date range, rows read, database CPU/IO, concurrent report count, and API timeout rate.

**Mitigation:** Use organization/time/event indexes; restrict interactive date ranges; query only required columns; use read-only transactions; paginate exports; calculate durations from indexed event fields; schedule heavy exports if needed. Add a PostgreSQL summary projection only after normalized queries are measured and insufficient.

**Trade-off:** Range limits and asynchronous exports add UX complexity. A summary table adds write/update logic and consistency considerations.

### Bottleneck: time-zone grouping

**Why it occurs:** `timestamptz at time zone` grouping can prevent simple index-only aggregation and daylight-saving days vary.

**How to measure:** Compare plans and timing for UTC filtering plus local grouping across representative time zones.

**Mitigation:** Filter by UTC instants first, group only the selected result set in the organization's zone, and store business date on queue rows for common daily operations.

**Trade-off:** Business-date storage must be updated carefully when organization time-zone configuration changes; historical reports should preserve the intended zone.

## 7. WebSocket Connections

### Bottleneck: too many sessions or messages

**Why it occurs:** Every active customer/staff screen holds a connection, and broad queue topics may send updates to clients that do not need them.

**How to measure:** Active connections, subscriptions per destination, heartbeat failures, message rate/size, send queue depth, and update latency after commit.

**Mitigation:** Use scoped topics; send small versioned messages; avoid customer PII on shared topics; publish affected IDs and let clients refetch; configure heartbeat and idle cleanup; limit subscriptions per user; reconnect with backoff.

**Trade-off:** More REST reconciliation requests are generated, but correctness is clearer and message fan-out is smaller.

### Bottleneck: WebSocket publication blocking transactions

**Why it occurs:** A developer performs synchronous broker/network work inside a database transaction.

**How to measure:** Compare transaction duration with/without publisher, database lock hold time, and thread dumps.

**Mitigation:** Commit first and publish after commit. Use an outbox only if a measured missed-event requirement justifies it.

**Trade-off:** A process crash can lose an update, so reconnect/refetch is required. Adding an outbox later improves durability but adds operational work.

## 8. Concurrent Users

Expected concurrency must be defined per organization: active staff counters, customer token viewers, administrators, and booking requests. Load tests should model bursts at opening time and appointment slot release.

**Measure:** Throughput, p95/p99 latency, error/conflict rates, CPU, heap, database CPU/locks, pool pending count, WebSocket sessions, and notification backlog.

**Mitigate:** Bound payloads and query ranges, index active data, serialize only scarce resources, use stateless HTTP instances, and scale vertically first. If multiple backend instances are deployed, design shared WebSocket event delivery before enabling them.

**Trade-off:** Horizontal scaling adds deployment/session/event complexity. A single instance may be adequate for MVP and is easier to reason about.

## 9. Pagination

### Bottleneck: unbounded lists

**Why it occurs:** Customer history, audit logs, tokens, services, and analytics details grow over time.

**How to measure:** Response size, serialization duration, query rows, browser rendering time, and memory usage.

**Mitigation:** Require bounded `page`/`size` with a maximum such as 100, index the filter/order columns, and use keyset pagination for very large audit/history tables if offset becomes slow.

**Trade-off:** Keyset pagination is faster at scale but less flexible for jumping to arbitrary pages. Offset pagination is simpler for MVP.

## 10. N+1 Queries

**Risk areas:**

- Queue list fetching service/counter/customer labels.
- Appointment history fetching service and token details.
- Audit list fetching actor labels.
- Analytics grouping per service/counter.

**Measure:** Assert query count in repository/service tests and inspect Hibernate SQL in development.

**Mitigation:** Use explicit joins/projections and batch queries. Do not serialize lazy entities directly.

**Trade-off:** Projection DTOs require maintenance when response shapes change but avoid hidden query behavior.

## 11. Connection Pooling

Use HikariCP through Spring Boot defaults with explicit production bounds after measurement:

- Maximum pool size based on PostgreSQL capacity and measured concurrency.
- Connection timeout shorter than API timeout.
- Idle/max lifetime aligned with PostgreSQL/deployment limits.
- Leak detection only in development or controlled diagnostics.
- Separate long analytics work from short queue transactions by query limits first; separate pools only if measured and supported.

Do not increase pool size to hide slow SQL. That usually increases database contention.

## 12. Caching Decision

Do not add Redis, Kafka, Elasticsearch, or microservices for MVP. Low-risk service catalog or organization configuration may be cached in-process only after measuring repeated reads, with explicit invalidation on admin changes. Never cache correctness-critical token state, counter ownership, appointment capacity, or authorization decisions without a clear invalidation model.

## 13. Load and Performance Tests

- Load service browsing and availability reads.
- Burst final-slot booking and measure conflicts/latency.
- Burst walk-in token creation and verify unique numbers.
- Concurrent call-next across compatible counters.
- Customer/staff WebSocket session load with realistic event rate.
- Analytics ranges by day/week/month and export limits.
- Pagination at small and large history sizes.
- Database failure/slow-query behavior and pool recovery.

Record a baseline before optimizing and compare p95/p99 plus correctness results after each change.

## 14. Performance Acceptance Criteria

1. Common reads meet the 2-second p95 target under the agreed normal load.
2. Queue updates reach 95% of active views within 5 seconds under normal load.
3. No duplicate appointment/token/call occurs during performance and concurrency tests.
4. Queue candidate queries use intended indexes and bounded active data.
5. No critical endpoint produces N+1 queries in query-count tests.
6. Pagination prevents unbounded response payloads.
7. Connection pool remains below exhaustion under target load.
8. Analytics does not block queue transitions.
9. WebSocket publication is outside business transaction lock time.
10. Any Redis, Kafka, Elasticsearch, or service-extraction proposal includes measured evidence, expected gain, operational cost, and rollback plan.
