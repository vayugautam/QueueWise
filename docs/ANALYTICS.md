# QueueWise Analytics Design

**Version:** 1.0  
**Source of truth:** [PRD.md](PRD.md), [TRD.md](TRD.md), and [DATABASE_DESIGN.md](DATABASE_DESIGN.md)  
**Implementation:** PostgreSQL queries in the modular monolith

## 1. Analytics Principles

- Analytics is read-only and never changes queue state.
- PostgreSQL is sufficient for MVP; no warehouse, Kafka pipeline, Redis cache, or separate analytics service is introduced.
- All queries are organization-scoped and authorization-filtered.
- Time ranges use a half-open interval `[fromInclusive, toExclusive)`.
- Instants are stored as `timestamptz`; grouping uses the organization's IANA time zone.
- Missing timestamps are excluded from duration averages and reported as incomplete data rather than treated as zero.
- Current counts come from `tokens`; lifecycle durations come from `token_events` or timestamp columns.
- Large result sets are paginated or exported with a bounded range.

The QueueWise dashboard uses four database-side aggregate queries. The summary query
uses conditional `COUNT` and `AVG` expressions, the peak-hours query groups arrival
timestamps after converting them to the organization's time zone, the service-demand
query counts tokens through the `services` join, and the counter-performance query
aggregates completed service rows per counter. Each query returns only grouped values;
no `Token` entities are loaded into Java. This keeps memory use bounded by the small
result set, lets PostgreSQL use indexes and parallel query planning, and avoids moving
potentially millions of token rows over the application connection just to calculate
counts and averages.

## 2. Metric Definitions

### 2.1 Total tokens

**Definition:** Count of tokens created in the selected range, regardless of terminal status.

**Source fields:** `tokens.organization_id`, `tokens.created_at`, `tokens.service_id`, `tokens.queue_id`, `tokens.source`, `tokens.priority`.

```sql
select count(*) as total_tokens
from tokens t
where t.organization_id = :organizationId
  and t.created_at >= :fromInclusive
  and t.created_at < :toExclusive
  and (:serviceId is null or t.service_id = :serviceId)
  and (:source is null or t.source = :source)
  and (:priority is null or t.priority = :priority);
```

A token created before the range but completed inside it is not included in total token volume for that range; completion metrics use their own event timestamp.

### 2.2 Completed tokens

**Definition:** Count of tokens with a successful completion event in the selected range.

**Source fields:** `token_events.event_type = 'SERVICE_COMPLETED'`, `token_events.created_at`, `token_id`, organization and service through `tokens`.

```sql
select count(distinct te.token_id) as completed_tokens
from token_events te
join tokens t on t.id = te.token_id
where te.organization_id = :organizationId
  and te.event_type = 'SERVICE_COMPLETED'
  and te.created_at >= :fromInclusive
  and te.created_at < :toExclusive
  and (:serviceId is null or t.service_id = :serviceId);
```

`distinct` prevents duplicate rows from inflating the metric if a repair process is ever run. The lifecycle transition should normally allow one completion.

### 2.3 Cancelled tokens

**Definition:** Count of tokens whose successful `TOKEN_CANCELLED` event occurred in the range.

```sql
select count(distinct te.token_id) as cancelled_tokens
from token_events te
join tokens t on t.id = te.token_id
where te.organization_id = :organizationId
  and te.event_type = 'TOKEN_CANCELLED'
  and te.created_at >= :fromInclusive
  and te.created_at < :toExclusive
  and (:serviceId is null or t.service_id = :serviceId);
```

An appointment cancellation without a token is counted in appointment analytics, not token cancellation metrics.

### 2.4 No-show tokens

**Definition:** Count of tokens whose `TOKEN_NO_SHOW` event occurred in the range.

```sql
select count(distinct te.token_id) as no_show_tokens
from token_events te
join tokens t on t.id = te.token_id
where te.organization_id = :organizationId
  and te.event_type = 'TOKEN_NO_SHOW'
  and te.created_at >= :fromInclusive
  and te.created_at < :toExclusive
  and (:serviceId is null or t.service_id = :serviceId);
```

If a token can receive a correction event, count the final terminal outcome once per token using a window function or a terminal-event policy.

### 2.5 Average waiting time

**Definition:** Average elapsed time from the token becoming ready (`ready_at`/`arrival_at`) to its first successful `TOKEN_CALLED` event. Waiting time is not measured from appointment booking because an unarrived appointment is not waiting.

**Source fields:** `tokens.arrival_at` or readiness timestamp and `token_events.created_at` for `TOKEN_CALLED`.

```sql
with first_called as (
    select token_id, min(created_at) as called_at
    from token_events
    where organization_id = :organizationId
      and event_type = 'TOKEN_CALLED'
    group by token_id
)
select avg(extract(epoch from (fc.called_at - t.arrival_at))) as average_wait_seconds
from tokens t
join first_called fc on fc.token_id = t.id
where t.organization_id = :organizationId
  and t.arrival_at >= :fromInclusive
  and t.arrival_at < :toExclusive
  and t.arrival_at is not null
  and fc.called_at >= t.arrival_at
  and (:serviceId is null or t.service_id = :serviceId);
```

Tokens with no arrival or no call are excluded and counted separately as incomplete. For a token requeued and called twice, use the first call for customer waiting time; subsequent recall delay may be a separate operational metric.

### 2.6 Average service time

**Definition:** Average elapsed time from `SERVICE_STARTED` to `SERVICE_COMPLETED` for successfully completed tokens.

**Source fields:** first start and completion event timestamps, or `service_started_at` and `service_completed_at` where reliable.

```sql
with starts as (
    select token_id, min(created_at) as started_at
    from token_events
    where organization_id = :organizationId
      and event_type = 'SERVICE_STARTED'
    group by token_id
), completions as (
    select token_id, min(created_at) as completed_at
    from token_events
    where organization_id = :organizationId
      and event_type = 'SERVICE_COMPLETED'
      and created_at >= :fromInclusive
      and created_at < :toExclusive
    group by token_id
)
select avg(extract(epoch from (c.completed_at - s.started_at))) as average_service_seconds
from completions c
join starts s on s.token_id = c.token_id
join tokens t on t.id = c.token_id
where s.started_at is not null
  and c.completed_at > s.started_at
  and (:serviceId is null or t.service_id = :serviceId);
```

Incomplete service records are excluded and reported as `missing_service_duration_count`.

### 2.7 Peak hours

**Definition:** Local hour bucket with the highest number of tokens becoming ready/arriving in the selected range. This measures demand, not completion capacity.

**Source fields:** `tokens.arrival_at`, organization time zone.

```sql
select
    extract(hour from (t.arrival_at at time zone :organizationTimeZone))::int as local_hour,
    count(*) as token_count
from tokens t
where t.organization_id = :organizationId
  and t.arrival_at >= :fromInclusive
  and t.arrival_at < :toExclusive
  and t.arrival_at is not null
  and (:serviceId is null or t.service_id = :serviceId)
group by local_hour
order by token_count desc, local_hour asc;
```

Return all hour buckets for a chart and designate the first row as the peak. A business may later choose a different definition, such as completed service starts, but the metric must not silently mix demand and capacity.

### 2.8 Counter utilization

**Definition:** For each counter and range, active service seconds divided by available counter-session seconds, capped/report-flagged when the denominator is zero.

`counter_sessions` provide the available session intervals. Token events provide service intervals. Clip both intervals to the selected range before calculating.

```sql
with sessions as (
    select
        cs.counter_id,
        greatest(cs.started_at, :fromInclusive) as session_start,
        least(coalesce(cs.ended_at, :toExclusive), :toExclusive) as session_end
    from counter_sessions cs
    where cs.organization_id = :organizationId
      and cs.started_at < :toExclusive
      and coalesce(cs.ended_at, :toExclusive) > :fromInclusive
), service_intervals as (
    select
        t.assigned_counter_id as counter_id,
        greatest(t.service_started_at, :fromInclusive) as service_start,
        least(t.service_completed_at, :toExclusive) as service_end
    from tokens t
    where t.organization_id = :organizationId
      and t.service_started_at is not null
      and t.service_completed_at is not null
      and t.service_started_at < :toExclusive
      and t.service_completed_at > :fromInclusive
)
select
    s.counter_id,
    sum(extract(epoch from (s.session_end - s.session_start))) as available_seconds,
    coalesce(sum(extract(epoch from (i.service_end - i.service_start))), 0) as service_seconds,
    case when sum(extract(epoch from (s.session_end - s.session_start))) > 0
         then coalesce(sum(extract(epoch from (i.service_end - i.service_start))), 0)
              / sum(extract(epoch from (s.session_end - s.session_start)))
         else null end as utilization
from sessions s
left join service_intervals i on i.counter_id = s.counter_id
where s.session_end > s.session_start
group by s.counter_id;
```

The production query must prevent double-counting overlapping sessions and service intervals. A simpler implementation can aggregate token service seconds per counter separately, aggregate sessions separately, then join the two results. Paused time is not available capacity if the business policy records the pause in counter sessions; otherwise it must be reported as an explicit data limitation.

### 2.9 Service-wise demand

**Definition:** Count of tokens created/ready per service in the selected range, optionally split by source and priority.

```sql
select
    t.service_id,
    s.name as service_name,
    count(*) as total_tokens,
    count(*) filter (where t.source = 'APPOINTMENT') as appointment_tokens,
    count(*) filter (where t.source = 'WALK_IN') as walk_in_tokens,
    count(*) filter (where t.priority = 'URGENT') as urgent_tokens,
    count(*) filter (where t.priority = 'NORMAL') as normal_tokens
from tokens t
join services s on s.id = t.service_id
where t.organization_id = :organizationId
  and t.created_at >= :fromInclusive
  and t.created_at < :toExclusive
  and (:serviceId is null or t.service_id = :serviceId)
group by t.service_id, s.name
order by total_tokens desc, s.name asc;
```

## 3. Time Range and Time Zone Rules

- API accepts local date ranges plus an organization time zone, or explicit instants.
- Convert local `from` at `00:00` and local `to` at the next day boundary to UTC instants before querying.
- Use `[from, to)` to avoid double-counting adjacent reports.
- Store all lifecycle timestamps as `timestamptz`.
- Use `at time zone :organizationTimeZone` only for grouping and display.
- Daylight-saving transitions may create a local day with 23 or 25 hours; do not assume every day has 24 hours.
- A user may query only organizations and services allowed by authorization.
- Reject ranges longer than a configured maximum for interactive requests; use bounded exports for longer ranges.

## 4. Grouping Options

The MVP analytics API supports:

- Overall summary.
- Group by local day.
- Group by local hour for peak analysis.
- Group by service.
- Group by counter.
- Filter by source (`APPOINTMENT`, `WALK_IN`).
- Filter by priority (`NORMAL`, `URGENT`).

Every grouped query includes the grouping key, count, relevant duration metric, and an explicit missing-data count where applicable.

## 5. Missing and Invalid Data

- Missing `arrival_at`: exclude from waiting-time average and increment `missing_wait_start_count`.
- Missing `TOKEN_CALLED`: exclude from waiting-time average.
- Missing service start or completion: exclude from service-time average and increment `missing_service_duration_count`.
- Completion before start or negative interval: exclude and increment `invalid_duration_count`; alert operationally.
- Missing counter session: do not infer utilization as zero; return null plus `missing_session_data`.
- Deleted/deactivated services remain joinable for historical rows.
- Duplicate lifecycle events are deduplicated by token and event policy before aggregation.
- Null service/counter labels are reported as `UNKNOWN`, not silently dropped from counts.

## 6. Query and Index Requirements

Use the indexes defined in [DATABASE_DESIGN.md](DATABASE_DESIGN.md):

- `tokens(organization_id, created_at)` or service/date equivalent for volume.
- `tokens(organization_id, customer_id, created_at)` for history.
- `tokens(service_id, status, arrival_at)` for operational views.
- `token_events(organization_id, event_type, created_at)` for lifecycle metrics.
- `token_events(token_id, created_at)` for per-token duration reconstruction.
- `counter_sessions(organization_id, started_at)` for utilization.

Avoid N+1 queries. Use projections or native SQL for aggregate reports, return only selected columns, and paginate detail exports.

## 7. Analytics API Shape

`GET /api/v1/analytics/summary` returns:

```json
{
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-10-01T00:00:00Z",
  "timeZone": "America/New_York",
  "totalTokens": 1200,
  "completedTokens": 1075,
  "cancelledTokens": 60,
  "noShowTokens": 40,
  "averageWaitingSeconds": 842.3,
  "averageServiceSeconds": 615.8,
  "missingData": {
    "missingWaitStartCount": 8,
    "missingServiceDurationCount": 17,
    "invalidDurationCount": 1
  }
}
```

The API may expose peak-hour series, service demand, and counter utilization as separate endpoints so an interactive summary does not run every aggregate at once.

## 8. Performance and Retention

- Use read-only transactions for reports.
- Limit interactive ranges and export sizes.
- Analyze query plans for representative organization sizes.
- Do not run analytics at a lock level that blocks queue transitions.
- Retain source token, appointment, event, and audit data according to the configured retention policy.
- Add a PostgreSQL summary projection only after measured query latency or volume justifies it.
- A data warehouse or separate analytics service is future scope, not an MVP dependency.

## 9. Analytics Tests

1. Total token count filters by organization, service, source, priority, and time range.
2. Adjacent ranges do not double-count boundary timestamps.
3. Completed metric counts completion events, not token creation.
4. Cancelled and no-show metrics count their lifecycle event once.
5. Waiting average excludes unarrived and uncalled tokens.
6. Service average excludes missing and negative durations.
7. Peak hour uses organization-local time and handles daylight-saving dates.
8. Counter utilization clips intervals to the requested range.
9. Paused/unfinished sessions do not create false zero utilization.
10. Service demand splits appointment and walk-in tokens correctly.
11. Cross-organization data is never returned.
12. Deactivated services remain visible in historical analytics.
13. Large ranges are rejected or exported according to policy.
