# QueueWise Git Workflow

**Version:** 1.0  
**Repository model:** Protected `main` branch with pull requests

## 1. Branch Model

- `main`: production-ready code; protected and never pushed to directly.
- Short-lived branches from the latest `main`.
- Keep one coherent feature/fix per branch.
- Delete merged branches unless they are needed for an active release.

### Branch naming

Use lowercase kebab-case with a category and short issue/work name:

```text
feature/appointment-booking
feature/token-queue-engine
bugfix/final-slot-race
bugfix/websocket-reconnect
refactor/centralize-error-handling
chore/upgrade-java-build

docs/prd-and-trd

test/queue-concurrency
```

Feature branches use `feature/`; bug branches use `bugfix/`. Preferred categories are `feature/`, `bugfix/`, `refactor/`, `chore/`, `docs/`, and `test/`. Include an issue number when available:

```text
feature/123-appointment-booking
bugfix/145-duplicate-token-number
```

Avoid vague names such as `my-branch`, `changes`, or `final-fix`.

## 2. Starting Work

1. Ensure the working tree is understood before changing files.
2. Update local `main` from the remote.
3. Create a short-lived branch from current `main`.
4. Read the relevant PRD/TRD/design document.
5. Define the smallest vertical slice and its tests.

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/appointment-booking
```

Do not rewrite or discard another contributor's uncommitted work.

## 3. Commit Conventions

Use Conventional Commits:

```text
feat(appointments): prevent duplicate final-slot bookings
fix(queue): exclude paused counters from call-next
refactor(tokens): centralize lifecycle transition policy
test(queue): add simultaneous call-next coverage
docs(api): document invalid-state responses
chore(ci): cache Maven dependencies
```

Allowed primary types:

- `feat:` user-visible or capability addition.
- `fix:` bug correction.
- `refactor:` behavior-preserving code structure change.
- `test:` tests without production behavior change.
- `docs:` documentation-only change.
- `chore:` tooling, build, dependencies, or maintenance.

Optional scopes should identify the module: `auth`, `appointments`, `tokens`, `queue`, `counters`, `analytics`, `api`, `frontend`, `ci`.

### Commit rules

- Use an imperative subject: `prevent duplicate booking`, not `prevented duplicate booking`.
- Keep the subject concise, approximately 72 characters or fewer.
- Explain why and migration impact in the body when not obvious.
- Keep each commit buildable and reviewable where practical.
- Do not mix unrelated formatting, refactoring, and feature work.
- Do not commit secrets, generated credentials, local databases, or build output.

Example with body:

```text
fix(appointments): serialize final-slot capacity checks

Lock the PostgreSQL slot capacity row before the final availability check.
Map a losing concurrent booking to CAPACITY_UNAVAILABLE and avoid creating
an orphan token or notification.
```

## 4. Pull Requests

Every PR should:

- Target `main` unless a release branch is explicitly approved.
- Have a concise title using Conventional Commit style.
- Link an issue or explain the requirement.
- State what changed and why.
- Identify database migrations, environment variables, API changes, and operational impact.
- Include tests run and any known gaps.
- Include screenshots/GIFs for meaningful frontend changes.
- Be small enough for focused review.

Suggested PR description:

```markdown
## Summary
- Add transactional appointment booking.
- Prevent duplicate final-slot bookings with PostgreSQL capacity locking.

## Design
- Uses AppointmentService transaction and DTO boundary.
- Creates token after successful booking according to appointment policy.

## Tests
- `mvn test`
- PostgreSQL concurrency integration test
- Frontend booking flow test

## Operational notes
- Adds `APPOINTMENT_CAPACITY_LOCK_TIMEOUT` configuration.
- Requires migration V5.
```

## 5. Code Review Checklist

### Correctness

- Does the implementation match the PRD/TRD and relevant design document?
- Are happy path, validation, conflict, invalid-state, and authorization cases covered?
- Are appointment/token/counter transitions atomic and auditable?
- Are duplicate and concurrent requests safe?

### Security

- Are role and organization/resource scope checks server-side?
- Are DTOs used instead of entities?
- Are passwords, JWTs, secrets, and personal data protected?
- Are SQL queries parameterized and dynamic sort fields allow-listed?
- Are WebSocket destinations authorized?

### Data and transactions

- Are migrations reversible or safely deployable?
- Are constraints and indexes appropriate?
- Is the transaction boundary explicit?
- Are external provider calls outside critical transactions?
- Are audit and lifecycle events recorded correctly?

### Quality and maintainability

- Are names clear and classes focused?
- Is the code simpler than the problem requires, not more abstract?
- Are errors mapped centrally?
- Are comments explaining why rather than narrating code?
- Are tests deterministic and not dependent on sleeps or execution order?

### Operations

- Are configuration and environment variables documented?
- Are logs safe and useful?
- Are health/rollback implications understood?
- Does CI cover the changed slice?

## 6. Review Process

1. Author opens a draft PR when design feedback is useful.
2. CI runs compile, unit, security, integration, frontend, migration, and scan checks relevant to the change.
3. Author responds to review comments with a code change or explicit rationale.
4. Reviewer resolves only comments they are satisfied with; the author should not silently dismiss substantive concerns.
5. Required approval is obtained from at least one qualified reviewer; security/database changes may require an additional reviewer.
6. Squash or merge according to repository policy after checks pass.
7. Delete the branch after merge.

## 7. Merge Strategy

Use **squash merge** for ordinary feature/fix branches so `main` receives one coherent Conventional Commit and the branch's exploratory commits do not become permanent history.

Use a regular merge commit only for an approved long-lived release branch or a deliberate history-preservation case. Avoid rebasing shared branches. Never merge with failing required checks or unresolved security/database review.

## 8. Release Tags

Use annotated semantic-version tags:

```text
v0.1.0   initial MVP release
v0.1.1   patch release
v0.2.0   backward-compatible feature release
v1.0.0   first stable production release
```

Tag only a commit already merged to `main`, with CI green and release notes prepared. A release should identify:

- Application version.
- Database migration range.
- Required environment variables.
- API or frontend breaking changes.
- Rollback image/tag.
- Known issues.

Example:

```bash
git switch main
git pull --ff-only origin main
git tag -a v0.1.0 -m "QueueWise MVP"
git push origin v0.1.0
```

Do not move or delete a published release tag without an explicit incident/release decision.

## 9. Hotfixes

For an urgent production defect:

1. Branch from the production tag or protected `main` commit.
2. Name it `bugfix/<issue>-<short-name>`.
3. Add a regression test first where practical.
4. Keep the patch narrow.
5. Run security, database, and smoke checks.
6. Merge through a PR and create a patch tag such as `v0.1.1`.

## 10. Documentation Changes

When implementation changes an API, schema, queue policy, security behavior, deployment variable, or architecture decision, update the relevant document in the same PR. Documentation-only work uses `docs:` commits and should still be reviewed for correctness.
