# QueueWise Backend Test Plan

**Scope:** current implementation under `backend/src/main/java`  
**Test style:** JUnit 5, Mockito, MockMvc, Spring Boot slices, and PostgreSQL Testcontainers where database behavior matters.

## Test Strategy

- Unit tests isolate policies, domain invariants, mappers, security helpers, and exception mapping.
- Service tests mock repositories and publishers and verify authorization, transaction decisions, writes, and events.
- Web tests verify request validation, authentication/role boundaries, status codes, and response shapes.
- Repository tests use PostgreSQL for locks, JSONB, timestamps, indexes, and constraints.
- Integration tests cover registration/login, queue lifecycle, authorization, audit, analytics, and rollback behavior.

Every scenario below uses the same acceptance format: **Given**, **When**, **Then**.

## Module Coverage Matrix

| Module | Current implementation | Tests present | Required next tests |
|---|---|---|---|
| Authentication | Registration and login REST/service | Service, controller, integration | duplicate phone, phone registration, audit event, inactive user |
| Users | Entity and repositories; no public controller/service | Repository | organization scope, role mapping, inactive state |
| Services | Entity/repository; no public controller/service | Repository | active filtering, uniqueness, configuration validation |
| Counters | Entity/repository and queue operations | Queue service tests | pause/resume invariants, assignment scope |
| Appointments | Customer booking service and `POST /api/appointments` | Booking service tests | controller validation, capacity, cancellation policy, timezone boundaries |
| Tokens | Entity/repository and lifecycle in queue service | Queue service tests | terminal invariants, reasons, appointment linkage |
| Queue engine | Candidate policy and transactional service | Policy, service, concurrency | PostgreSQL race tests and rollback |
| Authorization | Security filter, method rules, organization checks | JWT/security tests | role matrix and cross-organization requests |
| Error handling | Global REST exception mapper | None | stable status/code/redaction contract |
| Analytics | PostgreSQL aggregate repository/service/controller | Repository | controller authorization and empty/null data |
| Concurrency | Pessimistic locks, optimistic versions, bounded retries | Structural and service tests | Testcontainers concurrent calls and number allocation |

## Authentication

1. **Given** an active organization, unique email, valid name, and a 12+ character password, **When** registration is submitted, **Then** a customer user is saved with a BCrypt hash, a JWT is returned, and no password/hash appears in the response or audit metadata.
2. **Given** an existing email in the same organization, **When** registration is submitted, **Then** the service returns `409` and does not save a second user.
3. **Given** an email with surrounding whitespace and mixed case, **When** registration is submitted, **Then** the stored identifier is normalized to lowercase and trimmed.
4. **Given** valid credentials for an active user, **When** login is submitted, **Then** authentication succeeds, `lastLoginAt` changes, and a JWT is returned.
5. **Given** wrong credentials, an unknown account, or an inactive account, **When** login is submitted, **Then** the API returns the same safe authentication error without account enumeration.
6. **Given** malformed registration or login JSON, **When** the controller validates it, **Then** it returns `400 VALIDATION_ERROR` with field errors.

## Users

1. **Given** users from two organizations, **When** a repository lookup is scoped to organization A, **Then** organization B users are never returned.
2. **Given** a user with CUSTOMER, STAFF, and ADMIN roles, **When** authorities are built, **Then** each role becomes the expected `ROLE_*` authority.
3. **Given** an inactive user, **When** the JWT filter resolves the principal, **Then** authentication is rejected and the request remains unauthenticated.
4. **Given** a password hash, **When** it is serialized to an API response or audit metadata, **Then** the hash is absent.

## Services

The current repository has no service-management REST surface. Add these tests when its application service exists:

1. **Given** an active service, **When** it is created with valid duration, capacity, and hours, **Then** it is saved and `SERVICE_CREATED` is audited.
2. **Given** a duplicate service name in one organization, **When** creation is attempted, **Then** it returns `409` and leaves the existing service unchanged.
3. **Given** a service update that changes policy, **When** it commits, **Then** `SERVICE_UPDATED` records safe before/after values and the actor.
4. **Given** a negative duration, zero capacity, invalid hours, or horizon overflow, **When** validation runs, **Then** it returns `400` without persistence.
5. **Given** an inactive service, **When** queue selection runs, **Then** it is excluded from eligible work.

## Counters

1. **Given** an available counter assigned to staff, **When** pause is requested with a reason, **Then** status becomes `PAUSED` and `COUNTER_PAUSED` is audited.
2. **Given** a paused counter, **When** resume is requested, **Then** status becomes `AVAILABLE` and `COUNTER_RESUMED` is audited.
3. **Given** an inactive counter, **When** pause is requested, **Then** the operation fails and state is unchanged.
4. **Given** a non-paused counter, **When** resume is requested, **Then** the operation fails without an audit success event.
5. **Given** staff assigned to counter A, **When** they operate counter B, **Then** the operation returns `409` and does not mutate B.

## Appointments

1. **Given** an open service and available capacity, **When** a customer books an interval, **Then** one appointment is created and `APPOINTMENT_CREATED` is audited.
2. **Given** an overlapping active appointment at capacity one, **When** another booking is attempted, **Then** it returns `409`.
3. **Given** a cancelled or completed appointment, **When** another appointment overlaps it, **Then** the overlap policy permits it if the business rule excludes that status.
4. **Given** a booked appointment before its cancellation cutoff, **When** the customer cancels, **Then** status changes and `APPOINTMENT_CANCELLED` is audited.
5. **Given** a booking outside service hours, closure, horizon, or organization timezone, **When** booking is attempted, **Then** it returns a safe validation/conflict error.
6. **Given** two simultaneous capacity-one bookings, **When** both transactions commit, **Then** at most one succeeds.

## Tokens

1. **Given** an open queue and customer in the same organization, **When** a walk-in is created, **Then** token number allocation, `TOKEN_CREATED`, and token lifecycle history are written in one transaction.
2. **Given** an appointment not checked in, **When** an appointment token is requested, **Then** creation is rejected.
3. **Given** a waiting token, **When** it is cancelled, skipped, or marked no-show, **Then** a required reason is stored and it cannot re-enter active work.
4. **Given** a serving token, **When** completion occurs, **Then** it becomes terminal, records completion time, releases the counter, and emits `TOKEN_COMPLETED`.
5. **Given** a terminal token, **When** any ordinary transition is attempted, **Then** it is rejected and no duplicate lifecycle event is written.
6. **Given** a token belonging to another organization, **When** an actor tries to mutate it, **Then** the request is rejected without mutation.

## Queue Engine

1. **Given** urgent and normal eligible tokens, **When** call-next runs, **Then** urgent work wins unless a normal token crossed the starvation threshold.
2. **Given** a checked-in appointment and equivalent walk-in, **When** candidates are scored, **Then** the appointment preference is deterministic.
3. **Given** a future or unarrived appointment, **When** candidates are evaluated, **Then** it is excluded.
4. **Given** an incompatible, inactive, paused, closed-queue, assigned, or non-waiting candidate, **When** selection runs, **Then** it is excluded.
5. **Given** equal scores, **When** selection runs repeatedly, **Then** ready time, appointment time, and UUID tie-breaks are stable.

## Live Queue

1. **Given** an authenticated user in the queue organization, **When** `GET /api/queues/{id}/live` is requested, **Then** waiting and active display-safe tokens are returned with positions and estimates.
2. **Given** a queue from another organization, **When** its live snapshot is requested, **Then** the service returns `409` and does not expose queue data.
3. **Given** token rows with customer names, email, or phone values, **When** the live snapshot is serialized, **Then** none of those fields appear in the response.
4. **Given** no waiting tokens, **When** the snapshot is requested, **Then** `waitingCount` and `estimatedWaitMinutes` are zero and the token list remains valid.

## Authorization

1. **Given** no JWT, **When** a protected endpoint is called, **Then** it returns `401` using the standard error shape.
2. **Given** a CUSTOMER token, **When** an admin, supervisor, or staff endpoint is called, **Then** it returns `403`.
3. **Given** a STAFF token, **When** audit or analytics admin data is requested, **Then** it returns `403` unless policy explicitly grants access.
4. **Given** a valid role in organization A, **When** a resource from organization B is requested, **Then** resource scope rejects it.
5. **Given** a malformed, expired, wrong-audience, wrong-issuer, or wrong-signature JWT, **When** it is presented, **Then** the request remains unauthenticated and no sensitive detail is returned.

## Error Handling

1. **Given** invalid bean-validation input, **When** the controller receives it, **Then** it returns `400 VALIDATION_ERROR` and bounded field errors.
2. **Given** a missing entity, **When** a service accesses it, **Then** it returns `404 RESOURCE_NOT_FOUND`.
3. **Given** a business conflict, **When** it is thrown, **Then** it returns `409 CONFLICT` without stack traces or secrets.
4. **Given** a database constraint violation, **When** it reaches the handler, **Then** it returns a safe `409`.
5. **Given** an unexpected exception, **When** it reaches the handler, **Then** it returns `500 INTERNAL_ERROR` without implementation details.
6. **Given** an `X-Correlation-Id`, **When** any error is returned, **Then** that ID is echoed; otherwise a new valid ID is generated.

## Analytics

1. **Given** tokens in and outside a range, **When** dashboard aggregation runs, **Then** only organization-scoped rows in `[start, end)` are counted.
2. **Given** missing duration timestamps, **When** averages are calculated, **Then** incomplete rows are excluded rather than treated as zero.
3. **Given** an organization timezone, **When** peak hours are grouped, **Then** local hour rather than UTC hour is returned.
4. **Given** two organizations with identical services, **When** demand is queried, **Then** rows cannot cross organization boundaries.
5. **Given** no matching rows, **When** the dashboard is requested, **Then** counts are zero, averages are null, and grouped lists are empty.
6. **Given** a non-admin caller, **When** analytics is requested, **Then** it returns `403`.

## Concurrency

1. **Given** two callers target one counter, **When** both call-next transactions run, **Then** at most one claims the same token.
2. **Given** a token is stolen between candidate selection and lock, **When** the first transaction rechecks, **Then** it retries within the bounded budget.
3. **Given** every candidate is stolen, **When** retries are exhausted, **Then** one controlled conflict is returned and no infinite loop occurs.
4. **Given** two walk-in requests share a queue, **When** they allocate numbers concurrently, **Then** unique queue token numbers are committed.
5. **Given** a stale entity version, **When** an update commits, **Then** the stale writer receives a controlled conflict.
6. **Given** a transaction rolls back, **When** after-commit WebSocket/audit handlers run, **Then** no false success side effect is published.

## Missing Edge Cases

- Login failure audit events and throttling are not currently implemented.
- Logout, password reset, permission changes, and staff management are not exposed.
- Appointment/service REST modules are absent, so their tests cannot be implemented until the use cases exist.
- No explicit clock abstraction exists for deterministic time-zone and DST tests.
- Queue update publisher APIs and WebSocket security currently prevent a clean backend build.
- Audit listener and analytics should receive integration tests against migrated PostgreSQL rather than only mocks.
- Pagination should reject or normalize invalid entity filter combinations explicitly.
- Analytics should validate `start < end` and enforce an interactive range limit.
