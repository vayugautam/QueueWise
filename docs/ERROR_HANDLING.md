# QueueWise Centralized Error Handling

**Version:** 1.0  
**Source of truth:** [API_SPECIFICATION.md](API_SPECIFICATION.md), [TRD.md](TRD.md), and [SECURITY_DESIGN.md](SECURITY_DESIGN.md)

## 1. Goals

QueueWise returns predictable, safe, actionable errors from REST APIs while keeping implementation details in server logs. Every error has a stable application code, HTTP status, timestamp, request path, correlation ID, and optional field errors.

The API must never return:

- Java stack traces.
- SQL text, table names, constraint names, or database URLs.
- Passwords, JWTs, secrets, provider credentials, or internal entity dumps.
- Different public messages that reveal whether a protected resource exists outside the caller's scope.

## 2. Error Taxonomy

| Code | HTTP status | Meaning | Typical source |
|---|---:|---|---|
| `VALIDATION_ERROR` | 400 | Input shape or field value is invalid | Bean Validation, DTO parsing |
| `AUTHENTICATION_ERROR` | 401 | Credentials/token are missing or invalid | Spring Security, JWT filter |
| `AUTHORIZATION_ERROR` | 403 | Authenticated user lacks role or resource scope | Method/security authorization |
| `RESOURCE_NOT_FOUND` | 404 | Resource is absent or outside caller scope | Service/repository lookup |
| `CONFLICT` | 409 | Request conflicts with uniqueness or current data | Duplicate booking, version conflict |
| `INVALID_STATE` | 409 | Requested lifecycle transition is not allowed | Token/counter/appointment state |
| `RATE_LIMITED` | 429 | Request frequency exceeds policy | Login/API rate limiter |
| `INTERNAL_ERROR` | 500 | Unexpected server failure | Unhandled exception |

`422 BUSINESS_RULE_VIOLATION` may be used for a valid request rejected by a policy such as a cancellation cutoff, but the required core taxonomy remains stable. If the team wants only the listed codes, map business-rule failures to `CONFLICT` or `INVALID_STATE` consistently.

## 3. Standard Error Response

This is the standardized error response contract used by every REST controller and by Spring Security's entry point and denied handler.

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "One or more fields are invalid.",
  "path": "/api/v1/appointments",
  "correlationId": "b1e3c9f5-5f62-4c5e-bc8a-8c5bc0e6fb5a",
  "errors": [
    {
      "field": "scheduledStart",
      "code": "INVALID_TIME",
      "message": "The selected start time is invalid."
    }
  ]
}
```

Rules:

- `timestamp` is server-generated UTC.
- `status` matches the HTTP status.
- `error` is a stable machine-readable code.
- `message` is safe and user-facing; it is not an exception message.
- `path` is the request path, without secrets or query values that may contain personal data.
- `correlationId` connects the response to structured logs.
- `errors` is empty or omitted for non-field errors.

## 4. Custom Exceptions

Place application exceptions under `common.exception` and keep them independent of HTTP where possible.

### `ValidationException`

Represents application-level validation that Bean Validation cannot express, such as a service being closed or an interval not matching service duration. Contains safe field errors and maps to `VALIDATION_ERROR`/400.

### `AuthenticationException`

Represents missing or invalid credentials in application boundaries. Spring Security has its own authentication exception types; the global entry point maps them to the public `AUTHENTICATION_ERROR` response.

### `AuthorizationException`

Represents a user who is authenticated but not allowed to access a role, organization, service, counter, appointment, or token. Maps to `AUTHORIZATION_ERROR`/403.

### `ResourceNotFoundException`

Represents a missing or deliberately hidden out-of-scope resource. Maps to `RESOURCE_NOT_FOUND`/404.

### `ConflictException`

Represents a duplicate booking, uniqueness conflict, stale optimistic version, or concurrent command conflict. Maps to `CONFLICT`/409.

### `InvalidStateTransitionException`

Represents an invalid appointment, token, or counter lifecycle operation. Maps to `INVALID_STATE`/409.

### `RateLimitExceededException`

Represents a request exceeding endpoint/account/IP policy. Maps to `RATE_LIMITED`/429 and may include a safe retry duration.

### `DatabaseConstraintException`

An internal translation boundary for known `DataIntegrityViolationException` causes. It must classify known constraints into safe application errors and map unknown database failures to `INTERNAL_ERROR`.

### `ExternalProviderException`

Represents a notification or other external provider failure. It should not roll back committed queue state unless the provider is explicitly part of the transaction, which it is not for MVP. It maps to a safe dependency error where appropriate.

## 5. `@RestControllerAdvice`

Create one global `GlobalExceptionHandler` annotated with `@RestControllerAdvice`. It converts exceptions from controllers and application services into `ApiErrorResponse` DTOs.

Recommended handlers:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBodyValidation(...) { ... }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintValidation(...) { ... }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedJson(...) { ... }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(...) { ... }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(...) { ... }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidState(...) { ... }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraint(...) { ... }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(...) { ... }
}
```

Security exceptions are handled by Spring Security's configured `AuthenticationEntryPoint` and `AccessDeniedHandler`, because many occur before a controller is invoked. They must use the same response DTO and correlation ID policy.

## 6. Validation Error Handling

### DTO validation

Use Jakarta Bean Validation on request DTOs:

- `@NotNull` for required IDs and timestamps.
- `@Size` for names, reasons, instructions, and notes.
- `@Positive`/`@PositiveOrZero` for durations and capacities.
- `@Pattern` or custom validators for phone/email where needed.
- Enum validation for priority, status, source, and reason codes.

`MethodArgumentNotValidException` becomes one `VALIDATION_ERROR` with one safe entry per invalid field. Do not return the default Spring error page or Java class names.

### Malformed JSON and parameters

Malformed JSON, invalid UUID/date/time formats, unsupported enum values, and invalid path/query parameters also map to `VALIDATION_ERROR`/400. The message should identify the input concept, not expose the parser's internal text.

### Business validation

Application services validate rules such as:

- Service is active and open.
- Appointment slot is within hours and capacity.
- Counter supports token service.
- Token transition is allowed.
- Cancellation cutoff has not passed.
- Urgent priority is permitted.

These throw typed exceptions rather than returning null or boolean error flags.

## 7. Database Constraint Errors

Database constraints are the final integrity boundary. Translate known failures without exposing PostgreSQL details:

| Database condition | Public response |
|---|---|
| Appointment exclusion/slot conflict | `CONFLICT`/409: slot unavailable |
| Duplicate confirmation code | `CONFLICT`/409: booking could not be duplicated |
| Duplicate appointment token | `CONFLICT`/409: appointment already has a token |
| Duplicate token number/display code | Retry bounded allocation; then `CONFLICT`/409 |
| Foreign key to missing/inactive scope | `RESOURCE_NOT_FOUND` or `CONFLICT` |
| Check constraint/state violation | `INVALID_STATE`/409 |
| Deadlock/serialization failure | Bounded retry; then `CONFLICT`/409 or safe `INTERNAL_ERROR` |
| Unknown database failure | `INTERNAL_ERROR`/500 with correlation ID |

Use a database error translator that classifies SQL state/constraint metadata from the exception cause. Log the technical constraint name only on the server. Never put it in the response.

Transactions roll back on failed writes. A handled constraint error must not leave an appointment, token, counter assignment, or audit record half-written.

## 8. Unexpected Exceptions

The catch-all handler:

1. Generates or retrieves a correlation ID.
2. Logs the exception and request context at `ERROR` with stack trace server-side.
3. Redacts authorization headers, passwords, tokens, personal data, and database details.
4. Returns `INTERNAL_ERROR`/500 with a generic message.
5. Does not reveal whether the failure occurred in SQL, JPA, a provider, or an internal class.

Example response:

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 500,
  "error": "INTERNAL_ERROR",
  "message": "The request could not be completed. Contact support with the correlation ID.",
  "path": "/api/v1/queues",
  "correlationId": "...",
  "errors": []
}
```

Do not retry unexpected failures automatically at the controller layer. Retry only known transient database/provider failures in the owning application service with a bounded policy.

## 9. Spring Security Error Handling

- Missing/invalid JWT: `AuthenticationEntryPoint` returns `401 AUTHENTICATION_ERROR`.
- Authenticated wrong role/scope: `AccessDeniedHandler` returns `403 AUTHORIZATION_ERROR`.
- Login credential failure: generic `401 AUTHENTICATION_ERROR`; do not reveal whether account exists.
- Rate limit: `429 RATE_LIMITED`, optionally with `Retry-After`.
- WebSocket authentication/subscription failures use safe STOMP errors and server logs; they do not expose security internals.

These handlers must use the same correlation ID and redaction policy as REST exceptions.

## 10. Logging Strategy

Use structured SLF4J/Logback logs. Each request log may include:

- Correlation/request ID.
- Timestamp, HTTP method, safe path, status, duration.
- Organization ID and actor ID when available.
- Error code and outcome.
- Exception class internally for unexpected failures.

Never log:

- Passwords or password hashes.
- Authorization headers, JWTs, refresh tokens, or private keys.
- Full phone/email data unless operationally justified and redacted.
- SQL with user values, database URLs, or credentials.
- Full request bodies for authentication or personal-data commands.

Log levels:

- `INFO`: successful lifecycle/configuration events where useful.
- `WARN`: rejected validation, authorization failure, rate limit, expected concurrency conflict.
- `ERROR`: unexpected failures, broken provider/database dependencies, audit persistence failures.
- `DEBUG`: local development diagnostics only; disabled or restricted in production.

Audit logs are business history and are not a replacement for technical logs. Audit payloads are append-only, scoped, and redacted.

## 11. Error Examples

### Invalid appointment slot

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "The appointment request is invalid.",
  "path": "/api/v1/appointments",
  "correlationId": "...",
  "errors": [
    { "field": "scheduledStart", "code": "OUTSIDE_SERVICE_HOURS", "message": "Choose a time during service hours." }
  ]
}
```

### Invalid credentials

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 401,
  "error": "AUTHENTICATION_ERROR",
  "message": "Invalid credentials.",
  "path": "/api/v1/auth/login",
  "correlationId": "...",
  "errors": []
}
```

### Wrong role

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 403,
  "error": "AUTHORIZATION_ERROR",
  "message": "You are not allowed to perform this action.",
  "path": "/api/v1/counters/uuid/pause",
  "correlationId": "...",
  "errors": []
}
```

### Missing/out-of-scope resource

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "The requested resource was not found.",
  "path": "/api/v1/tokens/uuid",
  "correlationId": "...",
  "errors": []
}
```

### Final appointment slot conflict

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "The selected appointment slot is no longer available.",
  "path": "/api/v1/appointments",
  "correlationId": "...",
  "errors": []
}
```

### Invalid token state

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 409,
  "error": "INVALID_STATE",
  "message": "This token cannot be completed in its current state.",
  "path": "/api/v1/tokens/uuid/complete",
  "correlationId": "...",
  "errors": []
}
```

### Rate limited

```json
{
  "timestamp": "2026-09-17T10:15:30Z",
  "status": 429,
  "error": "RATE_LIMITED",
  "message": "Too many requests. Try again later.",
  "path": "/api/v1/auth/login",
  "correlationId": "...",
  "errors": []
}
```

## 12. Client Behavior

React should map stable error codes to user-facing messages, preserve form input when safe, show field errors beside fields, and offer retry only for known transient failures. It must not display raw `message` values blindly if the backend later returns an unsafe integration message.

For `401`, clear/re-authenticate according to JWT policy. For `409`, refetch current resource state before allowing a retry. For `429`, honor `Retry-After`. For `500`, display the correlation ID and a generic recovery action.

## 13. Tests

1. Every custom exception maps to the expected status and error code.
2. DTO field violations aggregate into safe field errors.
3. Malformed JSON, UUID, date, and enum input returns `VALIDATION_ERROR`.
4. Database duplicate/exclusion violations map to safe `CONFLICT` responses.
5. Unknown database errors return `INTERNAL_ERROR` without SQL details.
6. Unexpected exceptions log server-side stack traces but return no stack trace.
7. Missing JWT returns `AUTHENTICATION_ERROR`.
8. Wrong role/scope returns `AUTHORIZATION_ERROR`.
9. Invalid token transition returns `INVALID_STATE`.
10. Rate limiting returns `RATE_LIMITED` and safe retry information.
11. Correlation ID is consistent across response and logs.
12. Responses never contain passwords, JWTs, SQL, connection strings, or internal class names.
13. Rollback tests prove failed commands do not leave partial appointments, tokens, counters, or audit records.
