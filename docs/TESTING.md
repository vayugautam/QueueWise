# QueueWise Testing Strategy

**Version:** 1.0  
**Source of truth:** [PRD.md](PRD.md), [TRD.md](TRD.md), and [QUEUE_ENGINE.md](QUEUE_ENGINE.md)  
**Primary tools:** JUnit 5, Mockito, Spring Boot Test, MockMvc, PostgreSQL integration tests, React testing tools

## 1. Testing Objectives

Testing must prove both ordinary feature behavior and the correctness properties that matter most to QueueWise: no duplicate appointments/tokens, valid lifecycle transitions, fair queue selection, scope-safe authorization, and consistency under concurrent requests.

The test suite should be layered:

- Fast unit tests for pure policies and mappers.
- Service tests for use cases with Mockito collaborators.
- Repository/database tests for SQL, constraints, and locks.
- Controller/MockMvc tests for HTTP contracts and security.
- WebSocket tests for authentication, subscription scope, and messages.
- End-to-end smoke tests for critical user journeys.

Tests should use fixed `Clock` instances and deterministic UUIDs where time/order matters. Avoid sleeping to create concurrency; coordinate threads with latches/barriers.

## 2. Test Organization

```text
src/test/java/com/queuewise/
  authentication/
  users/
  services/
  appointments/
  tokens/
  queues/
  counters/
  notifications/
  analytics/
  audit/
  security/
  integration/
  concurrency/
```

Use unit tests for policy and service logic, `@WebMvcTest` with Mockito for controller contracts, `@DataJpaTest` for repository/constraint behavior, and `@SpringBootTest` for integrated application wiring. Use a real PostgreSQL instance in integration/CI tests where row locks and exclusion constraints matter.

## 3. Unit Tests

Unit tests isolate classes with no Spring context and use JUnit 5 assertions and Mockito only at external boundaries.

### Authentication and security

- Password service calls `PasswordEncoder.encode` during registration.
- Password matching succeeds only for the correct password.
- JWT claims contain expected subject, organization, roles, issuer, audience, and expiry.
- JWT parser rejects wrong signature, algorithm, issuer, audience, malformed token, and expiry.
- Authority mapper maps roles to `ROLE_*` values.
- Current-user adapter returns ID/scope and rejects anonymous access.

### Services and appointments

- Service policy accepts a slot inside hours.
- Service policy rejects closures, past slots, invalid duration, and horizon overflow.
- Appointment overlap policy detects conflicts only for active statuses.
- Cancellation policy accepts before cutoff and rejects after cutoff.
- Time-zone conversion handles daylight-saving boundaries with a fixed clock.

### Tokens and lifecycle

- Allowed state transition map accepts each valid transition.
- Terminal tokens reject all ordinary outgoing transitions.
- Skip/no-show/cancel require reasons where policy says so.
- Idempotent repeated completion returns current state without a second event.
- Mapper never exposes password or internal fields.

### Queue policies

- Urgent token wins when no normal token is protected.
- Normal token past starvation threshold wins over newer urgent work.
- Aging score increases as wait increases.
- On-time checked-in appointment bonus breaks equal-score ties.
- Future unarrived appointment is ineligible.
- Counter compatibility and paused state filter candidates.
- Tie-break order is deterministic by ready time, appointment time, then UUID.

### Analytics and error handling

- Duration calculators exclude missing/negative timestamps.
- Time range uses inclusive start and exclusive end.
- Error mapper creates stable codes and redacts details.
- Database exception classifier maps known constraint categories safely.

## 4. Service Tests

Service tests use Mockito for repositories, clock, authorization, publishers, and providers. They verify transaction-oriented decisions and interactions.

### Authentication service

- Valid registration creates a customer role and encoded hash.
- Duplicate contact is rejected.
- Public registration cannot assign admin/staff role.
- Valid login delegates to `AuthenticationManager` and creates JWT.
- Invalid login returns a generic authentication failure.
- Deactivated user cannot authenticate.

### Appointment service

- Booking validates service, organization, date/time, capacity, and duplicate overlap.
- Final capacity lock is requested before final availability decision.
- Appointment and configured token are created in the same use case.
- Notification is requested only after successful commit boundary.
- Cancellation updates appointment/token consistently.
- No-show job is idempotent and does not change completed/cancelled appointments.

### Token and queue services

- Walk-in creates the correct service/source/priority token.
- Simultaneous command retry does not create duplicate lifecycle events.
- `callNext` rejects paused or incompatible counter.
- `callNext` locks/rechecks selected token before update.
- Start/complete/skip require assigned counter and authorized staff.
- Queue update publication receives the committed event, not a pre-commit state.

### Counter service

- Staff can select assigned available counter.
- Unassigned staff cannot select or pause counter.
- A counter with active token cannot release or call again.
- Pause/resume records reason and audit command.

## 5. Repository Tests

Use `@DataJpaTest` for mapping, query predicates, and constraints. Run important tests against PostgreSQL rather than only an in-memory database because lock and exclusion behavior differs.

### User/service/counter repositories

- Organization scope is included in lookups.
- Case-insensitive service/counter names are unique within organization.
- Inactive services/counters are excluded from operational queries.
- Counter-service join queries return only compatible counters.

### Appointment repositories

- Availability query counts only capacity-consuming statuses.
- Overlapping capacity-one appointments violate exclusion constraint.
- Customer history is ordered and paginated.
- Cancellation removes appointment from active capacity query.

### Token repositories

- Token number/display code unique per queue.
- Partial unique appointment-token constraint prevents a second token.
- Candidate query excludes terminal, assigned, incompatible, and paused cases.
- Queue ordering query is deterministic.
- `@Lock(PESSIMISTIC_WRITE)` or equivalent lock is applied to call/number allocation query.

### Audit/notification/analytics repositories

- Audit rows are appendable and not exposed through update/delete paths.
- Notification retry query selects only pending due records.
- Analytics projections return correct aggregates and organization scope.

## 6. Controller Tests

Use `@WebMvcTest` and `MockMvc` with mocked application services.

For every controller, verify:

- Valid DTO returns expected JSON/status.
- Missing/invalid fields return `400 VALIDATION_ERROR`.
- Unknown IDs return `404 RESOURCE_NOT_FOUND`.
- Business conflicts return `409 CONFLICT` or `INVALID_STATE`.
- Controller does not expose entities, password hashes, or internal exceptions.
- Correlation ID is returned.

Specific cases:

- Auth registration/login success and generic invalid credentials.
- Customer can access own appointment endpoint but not another's.
- Receptionist can create walk-in; customer cannot.
- Staff can call/start/complete only through permitted routes.
- Admin-only service/counter/staff endpoints reject non-admin users.
- Pagination and filter bounds reject invalid values.
- Error responses match the documented API shape.

## 7. Security Tests

Use Spring Security test support and MockMvc:

- Missing JWT returns `401 AUTHENTICATION_ERROR`.
- Expired JWT is rejected.
- Invalid signature/issuer/audience is rejected.
- Customer cannot call admin endpoint.
- Staff cannot access another organization.
- Staff cannot process incompatible counter/token.
- Customer cannot perform IDOR access to another appointment/token.
- Role claims are not accepted without valid JWT signature.
- CORS allows configured origin and rejects unknown origin.
- CSRF behavior matches bearer-only/cookie strategy.
- Repeated failed login is rate limited.
- Rate-limited response does not reveal account existence.
- Error responses do not contain stack traces or SQL.
- WebSocket CONNECT and SUBSCRIBE authorization matches REST scope.

## 8. Integration Tests

Use `@SpringBootTest` for critical vertical flows with real repositories and configured test services:

1. Register -> login -> browse service -> book appointment.
2. Check in appointment -> token enters queue.
3. Receptionist creates walk-in -> staff calls -> starts -> completes.
4. Customer cancels before cutoff -> token leaves active queue.
5. No-show job marks expired appointment/token exactly once.
6. Counter pause prevents new call and resume restores eligibility.
7. Notification provider failure does not roll back booking/completion.
8. WebSocket receives post-commit queue update.
9. Analytics reflects committed lifecycle events.
10. Audit log records security, configuration, and token lifecycle changes.

## 9. Database Tests

Database integration tests must use PostgreSQL behavior, migrations, and realistic constraints:

- Migrations create all required tables, indexes, FKs, checks, and partial/exclusion constraints.
- Duplicate service/counter names fail within one organization but succeed across organizations if policy permits.
- Duplicate confirmation codes fail safely.
- Appointment capacity-one overlap is prevented.
- Appointment capacity row locking prevents overbooking.
- Token number allocation remains unique under concurrent inserts.
- Token version/row lock prevents duplicate lifecycle transition.
- Foreign keys preserve historical references.
- Audit rows cannot be changed through ordinary application paths.
- Analytics queries use expected indexes and organization predicates.

## 10. WebSocket Tests

- Authenticated user connects with valid JWT.
- Expired/invalid JWT cannot connect.
- Authorized staff subscribes to service/counter topic.
- Customer cannot subscribe to another user's token stream.
- Cross-organization destination is rejected.
- Token called/start/completed/skipped events have correct message fields/version.
- Pause/resume events reach authorized staff.
- Transaction rollback produces no success message.
- Client reconnect/refetch corrects a missed event.
- Heartbeat timeout closes stale session.

## 11. Queue Algorithm Tests

Use table-driven JUnit 5 tests with fixed `Clock`:

| Scenario | Expected result |
|---|---|
| Empty eligible set | No token selected with explanatory result |
| Normal only | Oldest/highest aging score selected |
| Urgent and new normal | Urgent selected |
| Urgent and protected normal | Protected normal selected |
| Many urgent arrivals | Normal cannot remain beyond starvation threshold |
| On-time appointment versus equal walk-in | Appointment preference wins |
| Future appointment not checked in | Excluded |
| Late appointment | Configured late policy applied |
| Incompatible counter | Token excluded |
| Paused counter | No call possible |
| Cancelled/no-show token | Excluded |
| Equal scores | Ready time, appointment time, UUID tie-break |
| Two compatible counters | Different tokens selected |

The queue suite includes an explicit cancelled token test: a cancelled token must never be selected or re-enter the active queue.

## 12. Concurrency Tests

Use `ExecutorService`, `CountDownLatch`, and two real database transactions. Do not rely on timing sleeps.

### Duplicate appointment booking

Start two transactions against the final capacity. Release them together. Assert exactly one appointment commits and the other receives a capacity/constraint conflict. Assert no orphan token or notification exists for the loser.

### Simultaneous token creation

Start multiple walk-in commands for one queue. Assert token numbers/display codes are unique and every successful token has one creation event. Verify bounded retry behavior on unique conflicts.

### Two staff call next simultaneously

Start two `callNext` requests for the same counter and token set. Assert one token is assigned once; the second receives current-state/occupied-counter result. Repeat with two compatible counters and assert different tokens.

### Lifecycle races

- Call versus cancellation: exactly one valid transition wins.
- Pause versus call: no token is called after pause commits.
- Complete versus skip: exactly one terminal transition wins.
- Release versus new call: counter state remains consistent.

## 13. API Testing

Use MockMvc for contract tests and optionally a generated HTTP client/contract suite:

- Verify HTTP method/path/status/body against `API_SPECIFICATION.md`.
- Verify auth/role/resource scope for each endpoint group.
- Verify validation and standard error response shape.
- Verify pagination, filters, time zones, and safe export limits.
- Verify idempotency behavior for booking and lifecycle commands.
- Verify `429` behavior and `Retry-After` policy.

Do not use only happy-path endpoint tests; every documented business conflict needs an API test.

## 14. Frontend Testing

Use the team's React testing stack with mocked Axios/React Query and a STOMP test double:

- Registration/login forms show field errors and safe auth errors.
- Service browsing handles loading, empty, inactive, and API failure states.
- Booking flow handles unavailable final slot and preserves input.
- Token status updates after WebSocket events and reconnect refetch.
- Customer history does not show another user's data.
- Staff queue disables call while pending and shows conflict state.
- Staff start/complete/skip controls reflect lifecycle permissions.
- Pause/resume counter UI reflects server state.
- Admin service/counter/staff forms reject unauthorized routes.
- Analytics filters and export errors are visible.
- Responsive layouts work at supported viewport widths.

## 15. Test Data and Isolation

Use builders/factories for organization, users, services, counters, appointments, queues, tokens, and events. Every test owns its organization scope. Reset database state between integration tests with transactions or controlled cleanup; never depend on test execution order.

Use fixed IDs and clocks for policy tests. Use real random IDs only when testing uniqueness/collision behavior. Do not use production data or real notification credentials.

## 16. CI Test Stages

1. Compile and static checks.
2. Fast unit and Mockito service tests.
3. `@WebMvcTest` controller/security tests.
4. Repository and migration tests against PostgreSQL.
5. Concurrency/integration tests.
6. Frontend tests and build.
7. Container smoke test and security scans.

CI must fail on test failure, migration failure, coverage of a required critical path below the agreed threshold, or security test failure. Coverage percentage is a signal; correctness of queue, appointment, authorization, and transaction behavior is the quality gate.

## 17. Minimum Release Gate

Before MVP release, automated or approved manual evidence must exist for:

- Duplicate appointment booking.
- Simultaneous token creation.
- Two staff calling next simultaneously.
- Urgent handling and starvation prevention.
- Counter compatibility and paused counter.
- Cancellation and no-show.
- Invalid state transitions.
- Unauthorized API access and expired JWT.
- Rate limiting.
- WebSocket authorization and reconnect.
- Standard errors with no internal details.
