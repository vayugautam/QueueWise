# QueueWise Architecture Decision Records

**Version:** 1.0  
**Status:** Accepted for MVP unless superseded by a later ADR  
**Scope:** Modular-monolith QueueWise

## ADR-001: Modular Monolith

**Context:** QueueWise has tightly coupled appointment, token, counter, queue, notification, analytics, and audit workflows. The project needs simple local transactions and low operational overhead.

**Decision:** Build one Spring Boot modular monolith with explicit module/package boundaries and one PostgreSQL database.

**Alternatives considered:** Microservices; serverless functions; a distributed workflow platform.

**Reason:** A monolith supports atomic booking/call/completion operations, simpler local debugging, one deployment, and lower infrastructure cost. Module contracts preserve a future extraction path.

**Consequences:** One deployable unit and shared database; modules must enforce dependency boundaries and avoid direct cross-module repository access.

**Trade-offs:** Less independent scaling than microservices, but substantially less network, deployment, distributed-transaction, and observability complexity. Extract services only after measured need.

## ADR-002: Java Spring Boot

**Context:** The backend needs REST, dependency injection, validation, security, transactions, JPA, testing, and WebSocket support.

**Decision:** Use Java 25 with Spring Boot, Spring Web, Spring Security, Spring Data JPA, and Spring WebSocket.

**Alternatives considered:** Node.js/Express; .NET; Jakarta EE without Spring Boot.

**Reason:** Spring provides mature, testable integrations for the selected stack and Java 25 provides the latest LTS runtime.

**Consequences:** The team must understand Spring lifecycle, proxy-based transactions, security filters, and JPA behavior.

**Trade-offs:** More framework concepts and startup/memory overhead than a minimal HTTP framework, balanced by production conventions and ecosystem support.

## ADR-003: PostgreSQL

**Context:** Queue correctness requires transactions, constraints, row locks, interval/capacity protection, relational joins, and reliable reporting queries.

**Decision:** Use PostgreSQL as the authoritative operational and MVP analytics database.

**Alternatives considered:** MySQL; MongoDB; separate operational and analytics databases.

**Reason:** PostgreSQL supports UUIDs, `timestamptz`, partial/exclusion indexes, row locking, SQL aggregation, and strong integrity guarantees.

**Consequences:** Schema migrations, indexes, query plans, and connection pooling must be managed carefully.

**Trade-offs:** Relational schema design takes more upfront thought than a document store, but it prevents duplicate bookings and inconsistent queue state more directly.

## ADR-004: JWT Authentication

**Context:** React and the stateless REST backend need authenticated requests without server-side HTTP sessions. No external identity provider is required for MVP.

**Decision:** Use short-lived signed JWT access tokens validated by a custom Spring Security filter. Add database-backed hashed refresh tokens only if session renewal is needed.

**Alternatives considered:** Server sessions; OAuth2/OIDC provider; opaque access tokens with an introspection service.

**Reason:** JWT fits a single modular monolith and avoids an identity platform while remaining simple to test and deploy.

**Consequences:** Key rotation, expiry, claim validation, revocation limitations, and resource-scope checks are application responsibilities.

**Trade-offs:** Statelessness simplifies scaling but makes immediate access-token revocation difficult. Short expiry and current-resource authorization reduce that risk.

## ADR-005: REST APIs

**Context:** Customers, staff, and admin screens need predictable commands and queries with standard HTTP status, validation, authorization, and error responses.

**Decision:** Use versioned JSON REST APIs under `/api/v1` for business commands and reads.

**Alternatives considered:** GraphQL; RPC-only endpoints; WebSocket commands for all operations.

**Reason:** REST is familiar, easy to test with MockMvc, cache/read compatible, and appropriate for resource-oriented appointments, tokens, queues, counters, and admin data.

**Consequences:** Some workflows require multiple requests, and API DTO/versioning must be maintained.

**Trade-offs:** Less flexible than GraphQL for arbitrary screens, but clearer authorization and lifecycle semantics. WebSocket remains an update channel, not the source of command truth.

## ADR-006: WebSockets

**Context:** Active staff and customer screens need queue changes within seconds rather than repeatedly polling.

**Decision:** Use Spring WebSocket/STOMP with scoped topics and REST reconciliation.

**Alternatives considered:** Polling only; Server-Sent Events; external pub/sub broker.

**Reason:** STOMP provides structured subscriptions and works naturally with Spring. A single-instance in-process broker is sufficient for MVP.

**Consequences:** Connection authorization, heartbeats, reconnect behavior, stale sessions, and horizontal-scaling event delivery must be handled.

**Trade-offs:** More connection lifecycle complexity than polling. REST fallback and reconnect refetch preserve correctness without introducing Kafka/Redis.

## ADR-007: JPA/Hibernate

**Context:** Most QueueWise data is relational and benefits from repository abstractions, entity mapping, transactions, and declarative locking.

**Decision:** Use Spring Data JPA with Hibernate for standard persistence, with explicit JPQL/native SQL projections where query shape or performance requires it.

**Alternatives considered:** Plain JDBC everywhere; jOOQ; MyBatis; a non-relational ORM.

**Reason:** JPA reduces routine persistence code and integrates with Spring transactions and PostgreSQL.

**Consequences:** The team must avoid N+1 queries, accidental eager graphs, entity leakage, and misunderstanding flush/transaction behavior.

**Trade-offs:** JPA can hide SQL cost and is less ideal for every aggregate query, so analytics and queue queries may use explicit projections/native SQL. This is preferable to abandoning the ORM for all use cases.

## ADR-008: DTOs Instead of Exposing Entities

**Context:** JPA entities contain internal relationships, security-sensitive fields, persistence state, and mutable fields that should not be API-controlled.

**Decision:** Controllers accept request DTOs and return response DTOs through explicit mappers.

**Alternatives considered:** Returning entities directly; JSON views; generic map responses.

**Reason:** DTOs prevent mass assignment, lazy-loading leaks, accidental password exposure, and API coupling to database schema.

**Consequences:** Additional mapper classes and DTO maintenance are required.

**Trade-offs:** More code than direct entity serialization, but the security and contract stability benefits are important for production-style learning.

## ADR-009: Database-Generated Identifiers

**Context:** Public entity IDs must be non-guessable and unique across application instances. Human-readable token numbers have separate queue semantics.

**Decision:** Use PostgreSQL/application-generated UUID primary keys, with database defaults where practical. Use separate scoped token numbers/display codes for customer-facing queue references.

**Alternatives considered:** Sequential numeric IDs for all entities; client-generated arbitrary strings; UUIDs for display numbers.

**Reason:** UUIDs avoid predictable identifiers and cross-instance sequence coordination. Token numbers need readable per-queue numbering, so they are separate constrained values.

**Consequences:** UUID indexes are larger than integers and require consistent API parsing.

**Trade-offs:** Slight storage/index cost is accepted for safer public references and simpler distributed creation. Token display numbering still requires queue-row locking/unique constraints.

## ADR-010: Queue Scheduling Algorithm

**Context:** QueueWise must combine appointments and walk-ins, urgent and normal priority, multiple counters, compatibility, and fairness. Pure FIFO ignores urgency; pure priority can starve normal customers.

**Decision:** Use eligibility filtering, urgent preference, aging score, appointment bonus, deterministic tie-breakers, and a normal-token starvation threshold.

**Alternatives considered:** Pure FIFO; pure priority queue; round-robin only; weighted fair queue with an external scheduler.

**Reason:** The selected algorithm is understandable, testable, persistent in PostgreSQL, and guarantees that a normal token past the threshold cannot be bypassed indefinitely by newer urgent arrivals.

**Consequences:** The policy needs configuration, fixed-clock tests, careful candidate queries, and transparent explanation in operations.

**Trade-offs:** More complex than FIFO, but the complexity directly addresses the PRD's fairness requirement. It avoids a separate scheduling service.

## ADR-011: Database-Level Concurrency Control

**Context:** Multiple backend instances and users may book final capacity, create tokens, select counters, or call the same token simultaneously.

**Decision:** Use PostgreSQL unique/check/exclusion constraints, row-level pessimistic locks for scarce resources, optimistic version columns for ordinary edits, and bounded retries.

**Alternatives considered:** Java `synchronized`; JVM locks; in-memory queues; distributed lock service; serializing all requests.

**Reason:** Database guarantees work across processes and directly protect persisted invariants.

**Consequences:** Transactions must be short, lock ordering must be documented, and deadlock/serialization errors must be handled.

**Trade-offs:** Contention can increase latency for popular slots/queues. That is preferable to overbooking or duplicate calls; scale improvements must preserve the invariant.

## ADR-012: Docker

**Context:** Developers and CI need repeatable Java, frontend, and PostgreSQL environments; production needs versioned deployable artifacts.

**Decision:** Use multi-stage Docker images and Docker Compose for local development with backend, frontend, and PostgreSQL only.

**Alternatives considered:** Manual host setup; VM images; Kubernetes from day one.

**Reason:** Docker makes the selected stack reproducible without introducing a platform layer.

**Consequences:** Image security, resource limits, health checks, secrets, volumes, and migration ordering must be managed.

**Trade-offs:** Container tooling adds build and debugging concepts, but it reduces “works on my machine” drift. Production may use managed PostgreSQL rather than a database container.

## ADR-013: Monolithic Deployment for MVP

**Context:** The initial team and expected product scale do not require independent service deployments. Core operations share transactions and one database.

**Decision:** Deploy one backend application, one frontend artifact/container, and PostgreSQL, initially with one backend replica unless measured requirements dictate otherwise.

**Alternatives considered:** Microservice deployment; Kubernetes; independently deployed queue/analytics/notification services.

**Reason:** One deployment is easier to monitor, debug, roll back, and operate. It matches the modular-monolith architecture and avoids premature infrastructure.

**Consequences:** A backend process or database outage affects all modules. Horizontal scaling requires a deliberate WebSocket event and session strategy.

**Trade-offs:** Less fault isolation and independent scaling, accepted for MVP. The module boundaries and API/event contracts preserve a future evolution path.

## Decision Review Rules

Revisit an ADR only with evidence such as measured p95/p99 latency, database saturation, team ownership needs, availability requirements, security findings, or deployment constraints. A replacement ADR must explain the old decision, new evidence, migration plan, and rollback strategy.
