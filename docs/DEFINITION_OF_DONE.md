# QueueWise Definition of Done

**Purpose:** A QueueWise feature is complete only when its behavior, security, persistence, API, UI, tests, and documentation are coherent and reviewable. This checklist applies to every feature, from a small validation change to a cross-module workflow.

## How To Use This Checklist

- Copy the checklist into the feature issue or pull request.
- Mark every applicable item complete with a link to evidence.
- Mark non-applicable items `N/A` with a short reason; do not leave them ambiguous.
- A feature is not done when code merely compiles or the happy path works.
- Any item marked `Blocked` requires an owner, reason, and follow-up issue before release.

## Feature Identity

- [ ] Feature name and owner are recorded.
- [ ] Related PRD requirement IDs are listed.
- [ ] Scope and explicit non-goals are written.
- [ ] Dependencies on other features, migrations, providers, or roles are identified.
- [ ] Risk level and rollout considerations are recorded.

## 1. Requirements Are Clear

- [ ] User problem and intended outcome are stated.
- [ ] Acceptance criteria are specific and testable.
- [ ] Given/When/Then scenarios cover the normal flow.
- [ ] Given/When/Then scenarios cover validation failures and business conflicts.
- [ ] Given/When/Then scenarios cover authorization and organization scope.
- [ ] Given/When/Then scenarios cover empty, missing, duplicate, stale, and terminal states where relevant.
- [ ] Time-zone, date-range, status, ownership, and idempotency rules are explicit where relevant.
- [ ] Privacy and data-minimization expectations are explicit.
- [ ] Requirements are traceable in [TRACEABILITY.md](TRACEABILITY.md).

## 2. Implementation Exists

- [ ] The owning backend module and application-service boundary are identified.
- [ ] The feature is implemented in the owning abstraction rather than duplicated in controllers or UI.
- [ ] Public interfaces and DTOs are stable, bounded, and safe to expose.
- [ ] Business invariants are enforced server-side.
- [ ] Transactions have the correct boundary and read-only operations use read-only transactions.
- [ ] Concurrency and locking behavior are deliberate for shared state.
- [ ] External side effects are after-commit or otherwise failure-isolated where appropriate.
- [ ] Frontend behavior uses the backend contract rather than duplicating business logic.
- [ ] No unrelated refactors or generated noise are included.

## 3. Validation Exists

- [ ] Request DTOs validate required fields, formats, lengths, ranges, and enum values.
- [ ] Service-level validation covers rules that cannot be expressed in DTO annotations.
- [ ] Database constraints remain the final integrity safeguard.
- [ ] Invalid dates, time zones, identifiers, and state combinations are handled where relevant.
- [ ] Validation failures return the standard QueueWise error shape.
- [ ] Validation does not mutate data or emit false success events.

## 4. Authorization Exists

- [ ] Authentication requirements are explicit for every endpoint.
- [ ] Required roles are enforced with method or endpoint security.
- [ ] Organization scope is checked using the authenticated principal, not client-provided identity.
- [ ] Resource ownership, assignment, service, counter, and status scope are checked where relevant.
- [ ] Cross-organization access has a negative test.
- [ ] Unauthorized and forbidden responses are safe and do not reveal sensitive resource existence.
- [ ] Authorization is enforced in the backend; frontend guards are only usability protection.

## 5. Error Handling Exists

- [ ] Expected not-found, conflict, validation, authentication, authorization, and dependency failures are mapped.
- [ ] Errors use stable QueueWise error codes and messages.
- [ ] Error responses include or preserve correlation IDs.
- [ ] Unexpected errors do not expose stack traces, SQL, credentials, JWTs, or internal paths.
- [ ] Retries are bounded and limited to known transient failures.
- [ ] Rollback behavior is verified for failed multi-write operations.
- [ ] The UI renders loading, empty, error, retry, and success states where applicable.

## 6. Tests Exist

- [ ] Unit tests cover pure policy, domain invariants, mappers, and calculations.
- [ ] Service tests cover repository interactions, authorization, transaction decisions, and emitted events.
- [ ] Controller tests cover request validation, response schema, status codes, and authorization boundaries.
- [ ] Repository tests cover predicates, organization scope, constraints, and indexes where relevant.
- [ ] PostgreSQL/Testcontainers tests cover PostgreSQL-specific SQL, locks, JSONB, timestamps, and constraints where relevant.
- [ ] Integration tests cover the most important end-to-end workflow.
- [ ] Concurrency tests cover duplicate processing, allocation, stale versions, or race windows where relevant.
- [ ] Tests include the acceptance scenarios and edge cases, not only the happy path.
- [ ] Test names describe behavior and follow the Given/When/Then intent.
- [ ] Test evidence is linked in the pull request or feature record.

## 7. API Documentation Exists

- [ ] Every public endpoint has OpenAPI documentation.
- [ ] Each endpoint documents summary and behavior description.
- [ ] Authentication and roles are documented.
- [ ] Request parameters, request body schema, and validation rules are documented.
- [ ] Response schema and representative success example are documented.
- [ ] All relevant status codes and standardized errors are documented.
- [ ] Examples contain no real personal data, passwords, JWTs, or secrets.
- [ ] OpenAPI paths, DTOs, and examples match the actual implementation.
- [ ] API version and base path are correct.
- [ ] Breaking changes are called out and migration guidance is provided.

## 8. Database Changes Are Documented

- [ ] Required migration is added, ordered, and reversible or has a documented recovery plan.
- [ ] Tables, columns, foreign keys, checks, unique constraints, and indexes are documented.
- [ ] Nullability, defaults, retention, and ownership are explicit.
- [ ] Existing data migration and backfill behavior are defined where relevant.
- [ ] Organization and resource-scope predicates are supported by the schema/query design.
- [ ] Query performance and expected indexes are reviewed for new reads.
- [ ] Locking, isolation, and transaction implications are documented for writes.
- [ ] Sensitive data classification, retention, and deletion behavior are documented.
- [ ] PostgreSQL behavior is tested when H2 cannot faithfully represent it.
- [ ] [DATABASE_DESIGN.md](DATABASE_DESIGN.md) and relevant design documents are updated.

## 9. Frontend Integration Exists Where Applicable

- [ ] The route and navigation entry are added or intentionally excluded.
- [ ] The page/component uses the shared API client and authentication state.
- [ ] Server state uses React Query where appropriate.
- [ ] Loading, empty, error, retry, disabled, success, and permission states are implemented.
- [ ] Forms show field validation and safe API errors.
- [ ] UI does not reimplement authorization, queue ordering, capacity, or other server rules.
- [ ] Sensitive fields are not displayed or stored unnecessarily.
- [ ] Responsive behavior works from 360 px through 1440 px.
- [ ] Keyboard navigation, labels, focus behavior, and icon tooltips are present where relevant.
- [ ] Existing navigation and deep links remain valid or have compatibility redirects.
- [ ] Frontend build and applicable browser tests pass.

## 10. Logging Exists Where Appropriate

- [ ] Security and operational events are logged at an appropriate level.
- [ ] Logs include correlation ID and safe organization/actor context where useful.
- [ ] Logs are structured enough to support diagnosis and monitoring.
- [ ] Passwords, password hashes, JWTs, authorization headers, secrets, and unnecessary personal data are never logged.
- [ ] Untrusted values are bounded and safely encoded.
- [ ] Audit events are used for business accountability; technical logs are not treated as audit history.
- [ ] Audit metadata is allow-listed or sanitized before persistence.
- [ ] Log volume and retention impact are considered.

## 11. Security Implications Are Reviewed

- [ ] Authentication and authorization impact is reviewed.
- [ ] Organization isolation and object-level access are reviewed.
- [ ] Input validation and injection risks are reviewed.
- [ ] Sensitive data exposure and privacy impact are reviewed.
- [ ] Session, JWT, cookie, CORS, CSRF, and browser-storage impact is reviewed where relevant.
- [ ] Rate limiting, abuse, replay, idempotency, and denial-of-service impact is reviewed where relevant.
- [ ] Secrets and provider credentials are externalized.
- [ ] Auditability and non-repudiation requirements are reviewed.
- [ ] Security tests or a documented exception are linked.

## 12. Code Is Reviewed

- [ ] At least one reviewer has reviewed the implementation and tests.
- [ ] Review covers correctness, authorization, data scope, failure behavior, and maintainability.
- [ ] Review verifies no duplicate business logic was introduced.
- [ ] Review checks migrations and rollback/deployment implications.
- [ ] Review checks API/OpenAPI and frontend synchronization.
- [ ] Review checks test quality and remaining gaps.
- [ ] All review comments are resolved or explicitly accepted by the owner.
- [ ] Static analysis, diagnostics, formatting, and build checks pass.

## 13. README and Documentation Are Updated

- [ ] README setup or usage instructions are updated when user/developer behavior changes.
- [ ] Relevant architecture, API, database, security, testing, analytics, concurrency, or deployment docs are updated.
- [ ] Traceability mapping is updated for new or changed requirements.
- [ ] Feature limitations and known edge cases are documented.
- [ ] Local development, environment variables, migration, and test commands are accurate.
- [ ] Screenshots or examples are updated when the user-facing flow materially changes.

## 14. Verification and Release Evidence

- [ ] Backend compile passes.
- [ ] Focused feature tests pass.
- [ ] Full backend test suite passes or documented failures are unrelated and tracked.
- [ ] PostgreSQL/Testcontainers tests pass in an environment with Docker when relevant.
- [ ] Frontend typecheck and production build pass.
- [ ] Browser/accessible viewport verification passes when the feature changes UI.
- [ ] Migration validation passes against a clean database and an upgrade database where relevant.
- [ ] No secrets, credentials, or generated build artifacts are included in the change.
- [ ] Deployment/rollback plan is known for schema or contract changes.
- [ ] The feature is linked to its PRD requirement and traceability row.

## Final Sign-Off

- [ ] All applicable checklist items are complete.
- [ ] All `N/A` items have a written reason.
- [ ] All known gaps have an owner and follow-up issue.
- [ ] Product owner accepts the behavior against the acceptance criteria.
- [ ] Engineering reviewer accepts the implementation and evidence.
- [ ] Security/data owner review is complete when the feature handles sensitive data, permissions, or external integrations.
- [ ] The feature is safe to release.

**Feature status:** `Done` / `Done with approved exceptions` / `Not Done`  
**Owner:**  
**Reviewer:**  
**PRD requirements:**  
**Evidence links:**  
**Approved exceptions and follow-ups:**  
