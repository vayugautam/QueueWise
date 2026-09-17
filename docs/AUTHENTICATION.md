# QueueWise Authentication and Authorization Design

**Version:** 1.0  
**Source of truth:** [PRD.md](PRD.md), [TRD.md](TRD.md), and [API_SPECIFICATION.md](API_SPECIFICATION.md)  
**Approach:** Spring Security with signed JWT access tokens  
**Scope:** Simple production-style student project; no OAuth2 provider

## 1. Design Summary

QueueWise uses Spring Security for request protection, password verification, role checks, and security context management. A custom JWT service creates and validates signed access tokens. A custom `OncePerRequestFilter` reads the bearer token and places an authenticated principal into Spring Security's `SecurityContext`.

The first implementation should use:

- One Spring Boot application.
- PostgreSQL-backed users and roles.
- BCrypt password hashing through Spring Security's `PasswordEncoder`.
- Short-lived access JWTs, initially 15 minutes.
- A database-backed, hashed refresh-token table only if the React session needs renewal without login.
- Stateless API requests; no HTTP session authentication.
- Role checks plus application-level organization/resource checks.

OAuth2 is not required because QueueWise is not integrating with an external identity provider in the MVP. Adding an OAuth2 authorization server or social-login provider would add concepts and infrastructure without solving a current requirement.

## 2. Maven Dependencies

Use the Spring Boot starters already selected by the project and add only the JWT library chosen by the team:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
</dependency>
```

The JJWT implementation and JSON modules must be included at runtime using the versions approved by the project. Do not add a second JWT library. Spring Security supplies the security pipeline; JJWT supplies signing and parsing without introducing OAuth2.

## 3. Security Package Structure

```text
com.queuewise.security/
  SecurityConfig.java
  JwtAuthenticationFilter.java
  JwtTokenService.java
  JwtAuthenticationEntryPoint.java
  RestAccessDeniedHandler.java
  QueueWiseUserDetailsService.java
  CurrentUser.java
  OrganizationAuthorizationService.java
  PasswordSecurityConfig.java

com.queuewise.authentication/
  AuthController.java
  AuthService.java
  RegistrationRequest.java
  LoginRequest.java
  AuthResponse.java
  RefreshTokenService.java       # only if refresh tokens are enabled
  RefreshToken.java              # only if refresh tokens are enabled
```

## 4. Spring Security Classes We Introduce

### `SecurityConfig`

A `@Configuration` class that creates the `SecurityFilterChain`, password encoder, authentication provider, authentication manager, CORS source, and exception handlers. It is the central declaration of HTTP security rules.

It does not contain business logic, database queries, or JWT parsing.

### `JwtAuthenticationFilter`

A `OncePerRequestFilter` that reads `Authorization: Bearer ...`, asks `JwtTokenService` to validate the token, builds an authenticated `Authentication`, and sets it in `SecurityContextHolder`. It must continue the chain when no token is present and must never trust claims before signature and expiry validation.

### `JwtTokenService`

An application class responsible for creating and parsing access JWTs. It owns the signing algorithm, required claims, issuer, clock, and expiration rules. It returns a safe result or throws an internal token exception that the filter translates into an unauthenticated request.

### `QueueWiseUserDetailsService`

An implementation of Spring Security's `UserDetailsService`. It loads an active user and their roles by login identifier and organization context. It returns Spring Security's `UserDetails`, not a JPA entity. It must not load inactive users as authenticated.

### `PasswordEncoder`

Spring Security's interface for one-way password hashing and matching. The configured implementation should be `BCryptPasswordEncoder` with an explicitly selected strength or `DelegatingPasswordEncoder` with BCrypt as the current default. Passwords are encoded during registration and verified with `matches`; they are never decrypted.

### `DaoAuthenticationProvider`

Spring Security's provider that uses `UserDetailsService` and `PasswordEncoder` to verify username/password credentials. It is appropriate for QueueWise's database-backed login and avoids writing password comparison logic in the controller.

### `AuthenticationManager`

Spring Security's coordinator for authentication providers. `AuthService` submits a `UsernamePasswordAuthenticationToken` to it during login. A successful result contains the authenticated principal and authorities; a failure is deliberately returned as a generic invalid-credentials response.

### `UsernamePasswordAuthenticationToken`

A Spring Security `Authentication` implementation used in two places: unauthenticated login input before verification, and an authenticated principal after successful verification. The login input must never be placed in a long-lived security context.

### `SecurityContextHolder`

Spring Security's holder for the current request's `SecurityContext`. The JWT filter places the authenticated principal here. Controllers and authorization checks read the current user from it. QueueWise must not use it as a substitute for resource-scope checks.

### `GrantedAuthority` and `SimpleGrantedAuthority`

Authorities represent permissions available to Spring Security checks. QueueWise maps role codes to authorities such as `ROLE_CUSTOMER`, `ROLE_STAFF`, and `ROLE_ADMIN`. The `ROLE_` prefix is important when using `hasRole`.

### `AuthenticationEntryPoint`

A handler for unauthenticated access to protected endpoints. `JwtAuthenticationEntryPoint` returns the standard JSON `401` error instead of an HTML login redirect.

### `AccessDeniedHandler`

A handler for authenticated users who lack the required role or scope. `RestAccessDeniedHandler` returns the standard JSON `403` error without exposing authorization internals.

### `CorsConfigurationSource`

A Spring Security integration that supplies allowed frontend origins, methods, headers, and credentials policy. Production origins must be configured rather than set to `*`.

### `@EnableMethodSecurity`

Enables method-level checks such as `@PreAuthorize`. Use it for coarse role checks on application services; resource ownership and organization scope still belong in application authorization code.

## 5. User and Role Model

The database stores a `users` row and many-to-many `user_roles` rows as defined in [DATABASE_DESIGN.md](DATABASE_DESIGN.md). Suggested role codes are:

- `CUSTOMER`
- `RECEPTIONIST`
- `STAFF`
- `SUPERVISOR`
- `ADMIN`

A JWT may contain role authorities and an organization ID, but those claims are not enough for sensitive resource checks. The application must verify that the user still exists, is active, belongs to the requested organization, and is assigned to the selected service or counter where required.

## 6. Registration

### Flow

1. `POST /api/v1/auth/register` receives a validated registration DTO.
2. The service validates organization status and normalizes email/phone.
3. It checks contact uniqueness as an early user-friendly check.
4. It encodes the password with `PasswordEncoder`.
5. It creates the user with `ACTIVE` status and the `CUSTOMER` role.
6. The database unique constraint remains the final duplicate protection.
7. The service writes a registration audit event without password data.
8. It returns a safe user DTO and either a token pair or requires a separate login, according to the selected UX.

### Registration rules

- Email or phone must be present and normalized.
- Password must meet minimum length and breach-resistance policy.
- Name fields have bounded lengths.
- The caller cannot select `ADMIN`, `STAFF`, or `SUPERVISOR` during public registration.
- A staff account is created only through an admin-controlled invitation or staff endpoint.
- Duplicate contact race conditions return a safe `409` response.

Registration must run in a transaction. It must not return `passwordHash`, raw password, or security tokens in logs.

## 7. Login

### Flow

1. The client sends login identifier, organization ID, and password to `/auth/login`.
2. `AuthService` creates an unauthenticated `UsernamePasswordAuthenticationToken`.
3. `AuthenticationManager` delegates to `DaoAuthenticationProvider`.
4. `QueueWiseUserDetailsService` loads an active user and authorities.
5. `PasswordEncoder.matches` verifies the password hash.
6. `JwtTokenService` creates a signed access token.
7. The service records a successful audit event and returns a safe `AuthResponse`.

Invalid identifier, wrong password, inactive user, and invalid organization context should produce the same public `401 INVALID_CREDENTIALS` response. Internally, the event may include a reason category without revealing it to the caller.

### Login response

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-17T10:30:00Z",
  "user": {
    "id": "uuid",
    "organizationId": "uuid",
    "roles": ["STAFF"]
  }
}
```

## 8. Password Hashing

Use BCrypt through Spring Security. A password hash is one-way and includes its salt and cost information. The application calls:

```java
String hash = passwordEncoder.encode(rawPassword);
boolean valid = passwordEncoder.matches(rawPassword, hash);
```

The raw password must exist only for the short duration of request processing. Never compare hashes with string equality, log raw passwords, return hashes in DTOs, or use reversible encryption for passwords.

The BCrypt strength should be selected by measuring acceptable login latency in the deployment environment. It should be high enough to resist guessing while keeping normal login responsive. If a `DelegatingPasswordEncoder` is used, support gradual rehashing after successful login when the stored algorithm is outdated.

## 9. JWT Creation

`JwtTokenService` creates an access token with claims similar to:

```json
{
  "iss": "queuewise-api",
  "sub": "user-uuid",
  "aud": "queuewise-web",
  "iat": 1726567200,
  "exp": 1726568100,
  "jti": "token-uuid",
  "org": "organization-uuid",
  "roles": ["ROLE_STAFF"]
}
```

Required properties:

- Sign with a strong asymmetric private key in production, or a sufficiently random HMAC secret for a small single-application deployment.
- Include issuer, audience, subject, issued-at, expiry, and a unique JWT ID.
- Keep claims small and non-sensitive.
- Do not include passwords, phone numbers, email addresses, or mutable permission details that would become dangerous when stale.
- Use a short access-token lifetime, initially 15 minutes.

The signing algorithm must be explicit and allow-listed. Never accept the algorithm from an untrusted token header.

## 10. JWT Validation

Validation must check, in this order conceptually:

1. The token exists and has the expected bearer format.
2. The signature is valid with the configured verification key.
3. The algorithm is allowed.
4. The issuer and audience match QueueWise.
5. `exp` has not passed, allowing only a small configured clock skew.
6. `nbf` and `iat` are reasonable if present.
7. The subject is a valid user identifier.
8. The organization and role claims have valid shape.

The filter must not treat a parse failure as a server error. It clears the context and lets the entry point return `401` for a protected endpoint. Public endpoints may continue without an authenticated principal.

For sensitive operations, load the current user or authorization state from PostgreSQL instead of relying solely on stale role claims. This makes account deactivation and assignment changes effective before the access token expires.

## 11. Authentication Filter

`JwtAuthenticationFilter` extends `OncePerRequestFilter` so one request is processed once by the filter. Its algorithm is:

1. Read the `Authorization` header.
2. If absent or not `Bearer`, continue the chain.
3. Extract and validate the token through `JwtTokenService`.
4. If the subject is missing or invalid, clear the context and continue or return a controlled `401`.
5. Build an authenticated `UsernamePasswordAuthenticationToken` with principal and authorities.
6. Set it in `SecurityContextHolder`.
7. Continue the filter chain.
8. Clear request context through Spring's normal request lifecycle.

The filter must not authenticate twice, issue tokens, query unrelated business data, or write success responses. Authentication errors are handled consistently by the configured entry point.

## 12. SecurityContext

Spring Security stores the current request authentication in `SecurityContextHolder`. Application code should access it through a small `CurrentUser` abstraction:

```java
public interface CurrentUser {
    UUID userId();
    UUID organizationId();
    Set<String> roles();
}
```

`CurrentUser` adapts the authenticated principal and throws a controlled authentication exception when no user exists. It prevents controllers from depending on JWT parsing details.

The context is request-scoped. Do not store it in static application state, pass it to asynchronous work without deliberate context handling, or use it as a substitute for checking that a customer owns a specific appointment.

## 13. Role-Based Authorization

Enable method security and use coarse rules at controllers or application services:

```java
@PreAuthorize("hasAnyRole('STAFF', 'SUPERVISOR', 'ADMIN')")
public TokenDto startService(StartServiceCommand command) { ... }
```

Use `hasRole('ADMIN')` for organization configuration. Use application authorization for:

- Customer owns the appointment or token.
- Staff is assigned to the selected counter.
- Counter supports the token's service.
- User belongs to the token's organization.
- Supervisor has the service/counter scope.
- Admin override includes a reason and audit event.

Never authorize only from a client-provided role, path ID, or hidden frontend control.

## 14. Protected Endpoints

Public or limited endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/services` only if public service browsing is enabled
- `GET /api/v1/services/{id}` only if public detail is enabled

Authenticated endpoints:

- `/api/v1/users/me`
- `/api/v1/appointments/**`
- `/api/v1/tokens/**`
- `/api/v1/queues/**`
- `/api/v1/counters/**`
- `/api/v1/analytics/**`
- `/api/v1/audit-logs/**`
- WebSocket handshake and subscriptions

Administrative endpoints:

- `/api/v1/users`, `/api/v1/users/staff`, and assignment endpoints: `ADMIN` or configured supervisor scope.
- `/api/v1/services` write operations: `ADMIN`.
- `/api/v1/counters` write operations: `ADMIN`.
- `/api/v1/admin/**`: `ADMIN`.

Default rule: deny all unlisted endpoints. Every WebSocket subscription must be authorized by organization and destination scope.

## 15. Token Expiration

Access tokens expire after 15 minutes initially. The API returns `401` after expiry; the client must not silently retry a failed mutating request unless it knows the request is idempotent.

Use a small clock-skew allowance, such as 30 to 60 seconds, only to account for server clock differences. Keep server clocks synchronized. Expiration is not a logout mechanism by itself; it limits the window of a stolen access token.

## 16. Refresh Strategy

### Recommended MVP choice

Start without refresh tokens if the student project can require login again after 15 minutes. This minimizes persistent credentials and revocation complexity.

### Optional database-backed refresh strategy

If the React application requires a longer session:

1. Issue a short-lived access token and a long-lived random refresh token.
2. Store only a hash of the refresh token in PostgreSQL.
3. Store user ID, organization ID, expiry, created time, last-used time, revoked time, and replacement token ID.
4. Send the raw refresh token only through a Secure, HttpOnly, SameSite cookie; do not place it in local storage.
5. On refresh, rotate the token and revoke the old one.
6. Detect reuse of a revoked refresh token and revoke the token family.
7. Re-check user active status and role scope before issuing a new access token.

Endpoints, if enabled:

- `POST /api/v1/auth/refresh`: accepts the refresh cookie and returns a new access token.
- `POST /api/v1/auth/logout`: revokes the current refresh token and clears the cookie.

Do not add Redis for refresh-token storage in the MVP; PostgreSQL is sufficient.

## 17. Logout Strategy

For access-token-only mode, logout is primarily client-side: remove the access token and close the WebSocket. The short-lived token cannot be individually revoked without a server-side deny list, which is intentionally avoided for MVP simplicity.

For refresh-token mode, logout revokes the refresh-token record and clears the cookie. The access token remains valid until its short expiry, so sensitive operations should also check current account status and resource authorization.

Logout is idempotent and must not reveal whether a token was previously valid.

## 18. Password Security

- Store only adaptive password hashes.
- Require a minimum length and reject obviously invalid input; do not impose arbitrary complexity rules that encourage unsafe reuse.
- Never send passwords in logs, audit JSON, analytics, exceptions, or emails.
- Use generic login and password-reset responses to avoid account enumeration.
- Throttle repeated failures by account and source.
- Invalidate refresh tokens after password change or reset.
- Require reauthentication for sensitive account or admin changes where appropriate.
- Do not allow users to set roles through public registration.
- Consider breached-password screening when an approved dependency or service is available; it is not required for the first implementation.

## 19. Secret Management

Required secrets are supplied through environment variables or a deployment secret store:

```text
JWT_PRIVATE_KEY or JWT_SECRET
JWT_ISSUER
JWT_AUDIENCE
JWT_ACCESS_TOKEN_MINUTES
DATABASE_PASSWORD
CORS_ALLOWED_ORIGINS
```

Rules:

- Never commit secrets to Git, `application.yml`, Docker images, frontend bundles, or test output.
- Prefer an asymmetric private signing key in production; keep the public verification key separately distributable if needed.
- Rotate signing keys with a planned key ID strategy. During rotation, validate old keys only for their remaining token lifetime.
- Fail startup when required production secrets are missing.
- Use separate secrets per environment.
- Do not print resolved configuration values that contain secrets.

Local development may use a developer-only `.env` file excluded by Git, but the application should document safe setup rather than include real credentials.

## 20. CORS

Configure CORS in one place through `CorsConfigurationSource`. Production should allow only the deployed React origin, for example `https://app.example.com`. Local development can allow the configured Vite origin.

Allowed methods should be limited to those used by the API, such as `GET`, `POST`, `PATCH`, `PUT`, and `OPTIONS`. Allow only required headers: `Authorization`, `Content-Type`, `Accept`, and `X-Correlation-Id`. Expose only safe response headers such as `Location` and correlation ID.

If refresh tokens use cookies, set `allowCredentials(true)` and never use wildcard origins. CORS is not authentication; it only controls browser cross-origin behavior.

## 21. CSRF Considerations

The API uses bearer access tokens rather than cookie-authenticated sessions, so traditional CSRF risk for access-token requests is low when the token is not automatically attached by the browser. Therefore, CSRF protection may be disabled for bearer-only API routes after documenting the decision.

If refresh tokens are stored in HttpOnly cookies, protect the refresh endpoint and any cookie-authenticated command with a CSRF strategy, such as SameSite cookies plus a synchronizer or double-submit token. Do not disable CSRF reflexively after introducing cookies. Do not store access tokens in cookies without revisiting this decision.

CORS, authentication, and CSRF solve different problems and must not be treated as interchangeable.

## 22. Brute-Force Protection

Implement simple protections at the authentication boundary:

- Count recent failed attempts by normalized login identifier and source IP.
- Return a generic invalid-credentials response for all credential failures.
- Apply increasing delay or temporary lockout after repeated failures.
- Reset the failure counter after successful login or a safe cooldown.
- Audit threshold events without storing passwords.
- Do not permanently lock an account based only on untrusted IP traffic; provide an admin recovery path.

A database-backed counter is adequate for a single-instance student project. If the application later runs many instances, the counter must move to a shared, deliberately chosen store; do not silently add Redis.

## 23. Rate Limiting

Rate-limit endpoints by endpoint sensitivity and actor:

| Endpoint group | Initial policy |
|---|---|
| Register | Low requests per IP and organization; duplicate attempts do not reveal account state |
| Login | Per IP plus normalized account identifier; stricter after failures |
| Refresh | Per refresh-token family and IP |
| Password reset | Per account and IP; generic response |
| Public service browsing | Higher read limit |
| Token and appointment commands | Per authenticated user with idempotency support |
| Admin operations | Authenticated user and organization limits |

For MVP, a servlet filter or a small application-level rate-limit component may be used if it has a clear storage strategy. Do not add a distributed rate-limiter dependency before measuring the need. Reverse-proxy limits can provide coarse protection in deployment.

Rate-limit responses use `429 RATE_LIMITED`, optionally with `Retry-After`, and do not expose internal counters.

## 24. Security Configuration Shape

The configuration should express the security policy clearly:

```java
@Bean
SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        JwtAuthenticationEntryPoint entryPoint,
        RestAccessDeniedHandler deniedHandler) throws Exception {
    return http
        .csrf(csrf -> csrf.disable()) // bearer-only API; revisit if cookies authenticate commands
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(deniedHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/services/**").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

This is a design shape, not a complete copy-paste implementation. The final project must decide whether service browsing is public, configure CORS origins, and add WebSocket handshake rules. The `csrf.disable()` comment must be removed or changed if authentication moves to cookies.

## 25. Authentication and Authorization Testing

### Unit tests

- Passwords are encoded and `matches` accepts only the original password.
- JWT creation includes required claims and expected expiry.
- JWT validation rejects wrong signature, issuer, audience, algorithm, malformed token, and expired token.
- Filter authenticates a valid bearer token and ignores missing headers.
- Filter does not overwrite an existing authenticated context.
- Role mapping creates the expected `ROLE_` authorities.
- Organization and resource authorization rejects cross-organization access.

### Spring Security and MockMvc tests

- Registration succeeds with valid input and never returns the password hash.
- Duplicate registration returns a safe conflict.
- Login succeeds with valid credentials.
- Invalid credentials return the same public response for unknown user and wrong password.
- Protected endpoint without token returns standard `401` JSON.
- Authenticated wrong-role request returns standard `403` JSON.
- Customer cannot access another customer's appointment or token.
- Staff cannot start service at an unassigned or incompatible counter.
- Admin-only configuration endpoints reject non-admin users.
- Expired token is rejected.
- CORS allows configured origin and rejects unknown origin.
- Rate-limit and lockout behavior is tested without making tests dependent on real time.

### Security acceptance criteria

1. No plaintext password or secret appears in database, response, log, or audit record.
2. All protected endpoints reject missing or invalid JWTs.
3. Role checks and organization/resource checks are enforced server-side.
4. Access tokens expire within the configured lifetime.
5. Refresh tokens, if enabled, are hashed, rotated, revocable, and stored in HttpOnly cookies.
6. Login and registration failures do not enable account enumeration.
7. CORS is limited to configured origins.
8. Security errors use the standard API error format without internal details.
