# QueueWise Concurrency Analysis

**Version:** 1.0  
**Source of truth:** [DATABASE_DESIGN.md](DATABASE_DESIGN.md), [QUEUE_ENGINE.md](QUEUE_ENGINE.md), and [APPOINTMENT_DESIGN.md](APPOINTMENT_DESIGN.md)

## 1. Concurrency Principles

QueueWise may run multiple backend instances in production. In-memory locks, synchronized methods, and process-local queues cannot guarantee correctness across instances. PostgreSQL constraints, transactions, row locks, and version checks are the correctness boundary.

Use:

- Database constraints for uniqueness and integrity.
- Pessimistic row locks for short, serialized allocation/selection decisions.
- Optimistic version checks for ordinary edits where conflicts are uncommon.
- Bounded retries for serialization/deadlock/known transient conflicts.
- Idempotency keys or command IDs for client retries after network timeouts.
- Publish WebSocket events only after the business transaction commits.

## 2. Optimistic versus Pessimistic Locking

### Optimistic locking

Add a `version bigint not null default 0` column to mutable aggregates. The update includes the version in its predicate:

```sql
update tokens
set status = :newStatus, version = version + 1
where id = :id and version = :expectedVersion;
```

If zero rows update, return a controlled stale-state conflict. Optimistic locking is simple and efficient when conflicts are rare.

### Pessimistic locking

Use `select for update` for a short transaction when two requests must serialize around one scarce resource. It is appropriate for final appointment capacity, queue token-number allocation, counter assignment, and `callNext`.

### Selected QueueWise strategy

Use pessimistic row locks for:

- Final appointment capacity decision.
- Human-readable token number allocation.
- Selecting/claiming the next token.
- Counter selection/release when only one staff session may own a counter.

Use optimistic versions for:

- Service configuration edits.
- Profile edits.
- Non-critical counter metadata.
- Token commands where a row lock already protects the critical path and version gives stale-client detection.

Do not lock an entire organization or table. Keep transactions short and acquire locks in a consistent order.

## 3. Token Number Generation

### Race condition

Two receptionists create walk-ins for the same queue and both read the current maximum token number as 42.

### Example interleaving

```text
A: select max(token_number) -> 42
B: select max(token_number) -> 42
A: insert token_number 43
B: insert token_number 43
```

### Incorrect outcome

Duplicate display numbers, confusing customers, or one request failing after partial side effects.

### Database-level solution

Use a queue row or queue-day sequence resource protected by `select for update`, and enforce unique `(queue_id, token_number)` and display-code constraints. Prefer an explicit queue counter column if numbers are sequential per queue/day.

```text
begin
lock queue row
nextNumber = queue.next_token_number + 1
update queue next_token_number = nextNumber
insert token with nextNumber
commit
```

### Application-level solution

`TokenNumberService` owns allocation and must run inside the token creation transaction. It retries a known unique conflict only a bounded number of times and never calculates numbers in a controller.

### Transaction boundary

Queue row lock, number increment, token insert, token event, and required audit record are one transaction.

### Locking strategy

Pessimistic lock on the single queue-day row. The unique constraint remains the final safeguard.

### Trade-offs

Serialization reduces maximum creation throughput for one queue, but token creation is a short operation and correctness of human-readable numbers matters more. Different service/day queues can allocate concurrently.

## 4. Appointment Booking

### Race condition

Two customers or the same customer submit a booking for a capacity-limited slot at the same time.

### Example interleaving

```text
A: count active appointments -> 0
B: count active appointments -> 0
A: insert appointment
B: insert appointment
```

### Incorrect outcome

The slot is overbooked or a customer receives a false confirmation.

### Database-level solution

Lock a capacity resource row for organization/service/date/slot, then recheck capacity. For capacity-one intervals, use a PostgreSQL exclusion constraint over `tstzrange`. For capacity greater than one, use a materialized slot-capacity row with `select for update` or an equivalent scoped lock.

### Application-level solution

`AppointmentService` performs a preliminary availability check for UX, but always repeats it after the lock. It translates exclusion/unique violations into `409 CONFLICT` and rolls back all related writes.

### Transaction boundary

Capacity lock, overlap check, appointment insert, optional token creation, and booking audit event are one transaction. Notifications are after commit or a transactional pending notification row.

### Locking strategy

Pessimistic lock on the slot/capacity row. Do not lock every appointment for the service or use a JVM synchronized block.

### Trade-offs

A lock can make requests wait briefly at popular slots, but it gives deterministic final capacity. Exclusion constraints are strong for capacity-one intervals but more complex to explain and migrate; capacity rows are simpler for fixed slots.

## 5. Last Available Slot

### Race condition

Two users see the final available slot in an availability response and submit booking concurrently.

### Example interleaving

```text
A: availability says remainingCapacity = 1
B: availability says remainingCapacity = 1
A: obtains capacity-row lock
A: rechecks and inserts; commits
B: obtains lock after A
B: rechecks and sees remainingCapacity = 0
```

### Incorrect outcome without protection

Both clients receive success, or one receives a late database error after notification/token side effects.

### Database-level solution

The final check occurs while holding the capacity row lock or through an exclusion constraint. The losing transaction cannot insert a conflicting appointment.

### Application-level solution

Availability responses are explicitly advisory. The booking service maps the losing outcome to `409 CAPACITY_UNAVAILABLE` and suggests alternatives. It does not retry the same slot blindly.

### Transaction boundary

Same as appointment booking: lock, recheck, insert, audit, commit.

### Locking strategy

Pessimistic capacity lock or exclusion constraint; no in-memory lock.

### Trade-offs

One user loses the race even though the UI showed availability, which is unavoidable. Clear conflict messaging and fresh alternatives are preferable to silent overbooking.

## 6. Calling Next Token

### Race condition

Two staff members call next at the same time, either on one counter or on two compatible counters.

### Example interleaving

```text
A: reads token X as WAITING
B: reads token X as WAITING
A: assigns X to counter 1
B: assigns X to counter 2
```

### Incorrect outcome

One customer is called at two counters, queue position is corrupted, or both staff begin serving the same token.

### Database-level solution

Lock the selected counter and token. Recheck status, assigned counter, compatibility, pause state, and fairness before updating. Use version/unique active assignment protection. `for update skip locked` may avoid waiting on unrelated candidate rows, but the selected token must still be revalidated.

### Application-level solution

`QueueEngine` is the only path for call-next. It performs atomic selection and returns current-state conflict after a bounded retry. The frontend's disabled button is not a concurrency control.

### Transaction boundary

Counter lock, candidate selection, token lock/recheck, token `WAITING -> CALLED`, counter state change, token event, and audit event are one transaction.

### Locking strategy

Pessimistic counter row lock first, then token row lock in a consistent order. For different counters, token lock prevents duplicate assignment; for the same counter, the counter lock serializes requests.

### Trade-offs

Pessimistic locking can briefly wait, but queue correctness is more important than maximum call throughput. A complex distributed queue would be unnecessary for MVP.

## 7. Counter Assignment

### Race condition

Two staff members select the same available counter, or a staff member selects a counter while an admin pauses/deactivates it.

### Example interleaving

```text
A: reads counter AVAILABLE
B: reads counter AVAILABLE
A: assigns current_staff_id = A
B: assigns current_staff_id = B
```

### Incorrect outcome

Both users believe they own one counter, causing conflicting calls and utilization data.

### Database-level solution

Lock the counter row during selection and enforce a partial unique index for one active counter session/owner. Update status and insert `counter_sessions` together.

### Application-level solution

`CounterAssignmentService` verifies staff assignment and counter status inside the transaction. Admin pause/resume uses the same lock and state policy.

### Transaction boundary

Lock counter, verify user/service scope, create session, set counter owner/status, and audit assignment in one transaction.

### Locking strategy

Pessimistic counter lock for select/release/pause. Optimistic version for metadata edits.

### Trade-offs

One counter has serialized ownership, which matches its business meaning. A user can still select another counter concurrently if policy allows.

## 8. Completing Service

### Race condition

Staff double-clicks Complete, a browser retries after timeout, or supervisor and staff complete/skip the same serving token concurrently.

### Example interleaving

```text
A: reads token SERVING
B: reads token SERVING
A: writes COMPLETED
B: writes COMPLETED or SKIPPED
```

### Incorrect outcome

Duplicate completion events, incorrect duration, counter released twice, or contradictory analytics.

### Database-level solution

Lock token or use `where status = SERVING and version = expected`. Enforce valid state transition in the transaction. Update token and counter together. Append one lifecycle event for the winning transition.

### Application-level solution

`TokenStateTransitionService` validates current state, counter ownership, actor scope, and idempotency/command ID. Repeated completion returns the existing completed result or a safe `409`.

### Transaction boundary

Token lock/version check, timestamp update, counter release, appointment completion update, token event, audit event, and post-commit update are one transaction.

### Locking strategy

Pessimistic lock for the active token/counter transition; optimistic version protects stale clients.

### Trade-offs

A second request may receive a conflict rather than a success, which is correct. Idempotency improves UX but requires storing command IDs or reliably recognizing the current result.

## 9. Queue Updates

### Race condition

Database state commits while WebSocket publication fails, or events are published before a transaction rolls back. Clients may see stale or impossible states.

### Example interleaving

```text
A: publishes TOKEN_COMPLETED
A: database transaction rolls back
Client: displays completed token that is still serving
```

### Incorrect outcome

Temporary client inconsistency, incorrect position display, or stale staff controls.

### Database-level solution

No database lock can guarantee an external WebSocket delivery. The database remains source of truth and stores versioned state/events. Publish only after commit.

### Application-level solution

Use transaction synchronization or an after-commit publisher. Include aggregate version and timestamp. On reconnect, the frontend refetches REST state. Add an outbox only if measured missed-event requirements justify it.

### Transaction boundary

Business state/event/audit commit first. WebSocket publication is outside the transaction but triggered only after successful commit.

### Locking strategy

Lock the business aggregate for the state change; do not lock waiting for WebSocket clients.

### Trade-offs

After-commit publication may lose a message if the process crashes immediately after commit. REST reconciliation keeps correctness without adding Kafka/Redis prematurely.

## 10. Deadlocks and Lock Ordering

Use a consistent order:

1. Organization/queue capacity row when needed.
2. Counter row.
3. Token row.
4. Appointment row when related.
5. Event/audit inserts.

Avoid taking locks in different orders across commands. Keep external provider calls and WebSocket work outside transactions. PostgreSQL deadlock/serialization errors receive a small bounded retry with jitter; after the limit, return a safe conflict and log the correlation ID.

## 11. Isolation Levels

Use PostgreSQL's default `READ COMMITTED` with explicit row locks for most commands. This is understandable and sufficient when every critical decision rechecks state under lock. Use stronger isolation such as `SERIALIZABLE` only for a measured case where scoped locks cannot express the invariant; it increases retries and contention.

## 12. Testing Concurrency

- Use two or more real transactions against PostgreSQL.
- Coordinate starts with `CountDownLatch`/barriers.
- Assert committed row state, event count, audit count, and notification rows.
- Repeat enough iterations to catch ordering assumptions without relying on sleeps.
- Test one backend instance and, where possible, multiple application contexts to disprove process-local lock assumptions.
- Verify deadlock/serialization retry is bounded.

## 13. Concurrency Acceptance Criteria

1. No duplicate token number/display code under simultaneous walk-ins.
2. No overbooking of final appointment capacity.
3. One token cannot be called by two counters.
4. One counter cannot be owned by two active staff sessions.
5. One serving token cannot be completed and skipped successfully twice.
6. No WebSocket success event is published for a rolled-back transaction.
7. All critical constraints hold with multiple application instances.
