# QueueWise Coding Standards

**Version:** 1.0  
**Language:** Java 25
**Scope:** Spring Boot modular monolith, React frontend, PostgreSQL

## 1. Guiding Principles

1. Prefer readable, explicit code over clever abstractions.
2. Keep classes focused on one responsibility.
3. Make invalid states difficult to represent.
4. Keep controllers thin and application services testable.
5. Use DTOs at API boundaries; never expose JPA entities directly.
6. Make transaction boundaries visible on use-case methods.
7. Use names that explain business meaning: `callNextToken`, not `process`.
8. Do not add a library or abstraction without a demonstrated problem.

## 2. Java Naming

- Use `UpperCamelCase` for classes, records, interfaces, and enums.
- Use `lowerCamelCase` for methods, variables, and parameters.
- Use `UPPER_SNAKE_CASE` for constants.
- Use nouns for types and verbs for methods.
- Avoid abbreviations except well-known terms such as `JWT`, `DTO`, `API`, `ID`, and `URL`.
- Use plural names for collections: `tokens`, `eligibleCounters`.
- Use precise booleans: `isActive`, `hasCapacity`, `canCancel`.

**Good:**

```java
boolean canCancelAppointment = appointmentPolicy.canCancel(appointment, now);
List<TokenDto> waitingTokens = tokenService.findWaitingTokens(queueId);
```

**Bad:**

```java
boolean flag = p.can(a, n);
List<TokenDto> list = service.getData(id);
```

## 3. Package Naming

Packages use lowercase dot-separated names rooted at `com.queuewise`:

```text
com.queuewise.appointments
com.queuewise.appointments.web
com.queuewise.appointments.application
com.queuewise.appointments.domain
com.queuewise.appointments.persistence
```

Do not use uppercase package names, generic `utils` packages for unrelated behavior, or a single package containing every class. Shared code belongs in `common` only when it is genuinely cross-module.

## 4. Class Naming and Interface Naming

- Classes use descriptive nouns: `AppointmentService`, `QueueEngine`, `TokenStateTransitionService`.
- Controllers end with `Controller`.
- Application use-case services end with `Service` when they orchestrate a use case.
- Repositories end with `Repository`.
- Mappers end with `Mapper`.
- Policies end with `Policy`.
- External boundaries use `Provider` or `Client`: `NotificationProvider`.
- Interfaces describe capability; implementations use a meaningful suffix such as `EmailNotificationProvider`.
- Avoid classes named `Manager`, `Helper`, `Util`, or `Common` unless their responsibility is genuinely clear.

## 5. Method Naming

Use verbs and make the business action visible:

```java
bookAppointment()
cancelAppointment()
callNextToken()
startService()
completeService()
findAvailableSlots()
calculateWaitEstimate()
```

Avoid vague methods:

```java
process()
handle()
doWork()
getData()
```

Queries should communicate whether they return one, many, or an optional result: `findById`, `findAllByQueueIdAndStatus`, `findOptionalActiveCounter`.

## 6. DTO Naming

DTOs are immutable Java records where practical and are named by direction/use case:

```text
RegisterRequest
LoginRequest
AuthResponse
CreateAppointmentRequest
AppointmentResponse
TokenSummaryResponse
UpdateServiceRequest
```

Avoid generic `AppointmentDTO` for every operation because it encourages accidental mass assignment. Request DTOs contain only client-settable fields. Response DTOs contain only fields safe and useful to clients.

**Good:**

```java
public record CreateAppointmentRequest(
        UUID serviceId,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd) {}
```

**Bad:**

```java
public class AppointmentDto extends AppointmentEntity {
    public String status;
    public UUID organizationId;
    public long version;
}
```

## 7. Exception Naming

Use names ending in `Exception` and representing a meaningful category:

```text
ResourceNotFoundException
ConflictException
InvalidStateTransitionException
AuthorizationException
RateLimitExceededException
```

Do not throw `Exception`, `RuntimeException`, or `IllegalStateException` from business code when a stable application exception is needed. Preserve the technical cause for server logs, but expose only the safe error code/message through `@RestControllerAdvice`.

## 8. Repository Naming

Repositories end in `Repository` and methods describe predicates:

```java
Optional<Appointment> findByIdAndOrganizationId(UUID id, UUID organizationId);
List<Token> findAllByQueueIdAndStatusOrderByArrivalAtAsc(UUID queueId, TokenStatus status);
```

For complex queue/analytics queries, use named repository methods, specifications, projections, or explicit native SQL with a comment explaining the invariant. Do not hide a large query in a generic `findAll()`.

Repositories do not enforce user authorization by themselves unless the scope is part of the query. Application services still perform role/resource policy checks.

## 9. Service Naming and Responsibilities

Application services orchestrate use cases and transactions:

```text
AppointmentService.book()
QueueEngine.callNext()
TokenStateTransitionService.complete()
CounterAssignmentService.select()
AnalyticsQueryService.summary()
```

A service should not become a god class. Extract a focused policy or calculator when logic can be tested independently:

```text
AvailabilityService
FairnessPolicy
WaitEstimateService
OrganizationAuthorizationService
```

Do not create an interface and implementation pair by default. Add an interface at an external boundary or when multiple implementations/test seams are real.

## 10. Controller Naming and Responsibilities

Controllers end in `Controller` and are organized by resource/module:

```java
@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController {
    private final AppointmentService appointmentService;

    @PostMapping
    AppointmentResponse book(@Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.book(request);
    }
}
```

Controllers should:

- Parse HTTP input.
- Trigger DTO validation.
- Delegate to an application service.
- Return a response DTO/status.

Controllers should not:

- Build JPA entities directly.
- Contain queue ordering or authorization policy.
- Catch every exception.
- Call external providers.
- Expose repository methods directly.

## 11. Constant Naming and Enum Naming

Constants use `UPPER_SNAKE_CASE` and should have a meaningful owner:

```java
private static final Duration DEFAULT_ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
```

Avoid global constants classes for unrelated values. Configuration belongs in typed `@ConfigurationProperties`.

Enums use `UpperCamelCase` type names and `UPPER_SNAKE_CASE` values:

```java
public enum TokenStatus {
    WAITING, CALLED, SERVING, COMPLETED, SKIPPED, NO_SHOW, CANCELLED
}
```

Persist enums as strings, not ordinal integers. Validate unknown external enum values as a safe `VALIDATION_ERROR`.

## 12. REST Conventions

- Use `/api/v1` versioning.
- Use plural nouns: `/appointments`, `/tokens`, `/counters`.
- Use HTTP verbs consistently: `GET` reads, `POST` commands/creation, `PATCH` partial update, `PUT` replacement, `DELETE` only where deletion is safe and defined.
- Use command subpaths for domain actions: `/tokens/{id}/complete`, `/counters/{id}/pause`.
- Return DTOs, appropriate status codes, and the standardized error response.
- Use pagination for histories, tokens, users, audit logs, and large lists.
- Make repeated commands idempotent where network retries are expected.
- Never put passwords, JWTs, or secrets in URLs.
- Use `Location` for newly created resources where useful.

## 13. Transaction Conventions

- Put `@Transactional` on public application-service use-case methods, not controllers.
- Use `@Transactional(readOnly = true)` for read-only service methods.
- Keep transactions short and deterministic.
- Do not call email/SMS providers or wait for WebSocket clients inside a database transaction.
- Publish queue updates after commit.
- Lock scarce resources in a documented order: capacity/queue, counter, token, appointment.
- Use `@Version` for optimistic edits and explicit pessimistic locks for final capacity, token numbers, and call-next.
- Roll back on known business failure and translate database constraints centrally.

**Good:**

```java
@Transactional
public AppointmentResponse book(CreateAppointmentRequest request) {
    Slot slot = slotRepository.lockAndLoad(request.serviceId(), request.scheduledStart());
    availabilityService.requireBookable(slot, request);
    Appointment appointment = appointmentFactory.create(request, slot);
    appointmentRepository.save(appointment);
    auditService.recordBooked(appointment);
    return mapper.toResponse(appointment);
}
```

**Bad:**

```java
public Appointment book(CreateAppointmentRequest request) {
    if (availabilityService.isFree(request)) {
        emailClient.sendConfirmation(request); // external call before commit
        return appointmentRepository.save(entityMapper.toEntity(request));
    }
    return null;
}
```

## 14. Logging Conventions

Use SLF4J parameterized logs and structured fields where available:

```java
log.info("Appointment booked appointmentId={} organizationId={} actorId={}",
        appointmentId, organizationId, actorId);
```

- Include correlation ID, organization ID, actor ID where safe, action, outcome, and duration.
- Use `INFO` for important successful operations, `WARN` for expected conflicts/denials, and `ERROR` for unexpected failures.
- Log the stack trace server-side for unexpected exceptions.
- Never log passwords, JWTs, refresh tokens, private keys, database passwords, or full sensitive request bodies.
- Avoid duplicate logs at controller, service, repository, and exception layers.

## 15. Comments and JavaDoc

Prefer clear names and small methods over comments. Comments should explain why, not repeat what the code says.

**Good:**

```java
// Lock the slot because availability can change between the read and insert.
Slot slot = slotRepository.lockAndLoad(serviceId, start);
```

**Bad:**

```java
// Get the slot.
Slot slot = slotRepository.find(serviceId, start);
```

Use JavaDoc for public application interfaces, non-obvious invariants, extension points, and security/concurrency assumptions. Do not write JavaDoc that merely repeats a method name. Keep examples and constraints current.

## 16. Validation Standards

Validate at three boundaries:

1. DTO validation for shape, required fields, lengths, and basic ranges.
2. Application service validation for ownership, roles, state, dates, capacity, and policy.
3. PostgreSQL constraints for uniqueness, foreign keys, checks, and concurrency invariants.

Client validation improves UX but never replaces server validation. Error responses use stable codes and field errors; they do not expose implementation exceptions.

## 17. Null Handling and Optional Usage

- Prefer non-null method parameters and return values where the domain requires a value.
- Use `Optional<T>` for repository methods that may find zero or one row.
- Do not use `Optional` for entity fields, DTO fields, method parameters, or collections.
- Return empty collections, not null lists.
- Convert absent resources to `ResourceNotFoundException` at the application boundary.

**Good:**

```java
Appointment appointment = appointmentRepository
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
```

**Bad:**

```java
Appointment appointment = repository.findById(id).orElse(null);
return appointment.getService().getName();
```

## 18. Exception Handling

- Throw typed exceptions for expected business failures.
- Let `@RestControllerAdvice` map exceptions to API errors.
- Do not catch and ignore exceptions.
- Do not return null to signal failure.
- Preserve causes for server logging without exposing them to clients.
- Use bounded retries only for known transient database/provider errors.
- Roll back partial writes when a command fails.

## 19. Database Naming

Use `snake_case` for PostgreSQL identifiers:

```text
users
user_roles
scheduled_start
assigned_counter_id
created_at
```

- Table names are plural nouns.
- Column names are lowercase snake case.
- Foreign keys end in `_id`.
- Timestamp columns end in `_at`.
- Boolean columns use `is_`/`has_` when helpful.
- Index names describe table and columns: `ix_tokens_queue_status_arrival`.
- Constraint names identify purpose: `uq_appointments_confirmation_code`, `fk_tokens_queue`.
- Avoid reserved words and quoted mixed-case identifiers.

## 20. SQL Naming and Style

- SQL keywords are uppercase in migrations and long queries.
- Table/column identifiers remain lowercase snake case.
- Use named parameters in repository queries when supported.
- Format joins and predicates on separate lines.
- Always include organization scope in organization-owned queries.
- Allow-list dynamic sort columns; never concatenate raw user input.
- Explain unusual locking/index choices in a migration comment or design document.

**Good:**

```sql
SELECT t.id, t.status
FROM tokens t
WHERE t.organization_id = :organizationId
  AND t.queue_id = :queueId
  AND t.status = 'WAITING'
ORDER BY t.arrival_at ASC, t.id ASC;
```

**Bad:**

```java
String sql = "select * from tokens where queue_id = '" + queueId + "'";
```

## 21. Git Commit Conventions

Use Conventional Commits:

```text
feat(appointments): prevent double booking of final slot
fix(queue): reject calls for paused counters
refactor(tokens): centralize lifecycle transitions
test(queue): cover starvation threshold
 docs(api): document token conflict responses
chore(ci): run PostgreSQL integration tests
```

Use imperative, concise subjects; keep the subject under approximately 72 characters; explain motivation and migration notes in the body when needed. Do not mix unrelated features, formatting churn, and dependency upgrades in one commit.

## 22. Review Checklist

- Names express domain intent.
- DTOs prevent mass assignment and entity exposure.
- Validation exists at the correct boundaries.
- Transaction and lock behavior is visible and tested.
- Errors are typed and centrally mapped.
- Logs contain correlation context but no secrets.
- Queries are scoped, parameterized, indexed, and free of obvious N+1 behavior.
- Tests cover happy path, invalid input, authorization, conflicts, and concurrency.
- Comments explain non-obvious reasons rather than narrating syntax.
