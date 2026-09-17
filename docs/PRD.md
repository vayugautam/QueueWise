# QueueWise Product Requirements Document

**Version:** 1.0  
**Status:** Draft  
**Date:** 2026-09-17

## 1. Product Overview

QueueWise is an appointment, token, and real-time queue management system for clinics, college offices, banks, salons, government counters, and similar service organizations. Customers can book appointments or receive walk-in tokens, see queue status and estimated wait, and receive notifications. Receptionists manage arrivals and walk-ins, staff operate service counters, and administrators configure the organization and review analytics.

The MVP is a responsive web application focused on reliable token flow, visible queue state, simple counter operation, and basic reporting. It does not attempt to replace an organization's clinical, banking, academic, billing, or customer-record system.

## 2. Problem Statement

Organizations often manage appointments and walk-ins through paper tickets, spreadsheets, verbal announcements, or disconnected systems. Customers cannot reliably predict when they will be served; staff lack a shared view of demand and capacity; receptionists manually handle exceptions; and administrators lack trustworthy data about wait times, service times, no-shows, and counter utilization.

QueueWise provides one authoritative, auditable view of appointments, tokens, queues, counters, and service outcomes.

## 3. Product Vision

Make every service queue predictable, fair, and easy to operate by giving customers timely information and giving service teams a shared real-time view of demand and capacity.

## 4. Goals

1. Let a customer book an appointment or obtain a walk-in token in under two minutes.
2. Show current token state, queue position, and approximate waiting time.
3. Let staff call and complete tokens with a few clear actions.
4. Let receptionists handle arrivals, cancellations, no-shows, and walk-ins without corrupting queue order.
5. Let administrators configure services, counters, staff, operating rules, and notifications without technical support.
6. Preserve an auditable history of important account, configuration, and queue events.
7. Provide basic operational analytics for staffing and service-level decisions.

## 5. Non-Goals

- Payments, billing, insurance claims, or medical records.
- Diagnosis, clinical decision support, or financial advice.
- Replacement of an organization's core system.
- Predictive AI staffing or complex workforce scheduling.
- Multi-location enterprise tenancy in the MVP.
- Exact waiting-time guarantees.
- Native mobile applications, video consultations, or marketplace discovery in the MVP.

## 6. Target Users

Customers, receptionists, front-desk operators, service staff, supervisors, operations managers, and organization administrators at clinics, college offices, banks, salons, and other service counters.

## 7. User Personas

- **Maya, appointment customer:** Wants a quick booking, confirmation, reminders, and confidence that she will not lose her place.
- **Daniel, walk-in customer:** Wants a token quickly and needs to know whether he can wait elsewhere without missing his turn.
- **Priya, receptionist:** Handles arrivals while answering questions; needs fast token creation and exception controls.
- **Omar, service staff:** Works at a counter; needs only compatible work and quick call/start/complete actions.
- **Elena, operations admin:** Configures services, counters, staff, rules, and reporting; needs safe changes and trustworthy data.

## 8. User Journeys

### 8.1 Customer

1. Register with name, phone or email, and password.
2. Log in and select an organization and service.
3. Select an available date and time.
4. Confirm the booking and receive an appointment and token reference.
5. Monitor token status, position, and estimated wait.
6. Receive configured reminder and near-turn or called notification.
7. See that staff called the token and started service.
8. See completion and completion time after service ends.

### 8.2 Walk-In

1. Customer approaches the receptionist.
2. Receptionist identifies or creates the customer when required.
3. Receptionist selects the service and permitted priority.
4. QueueWise creates a walk-in token with source, arrival time, service, priority, and compatible counters.
5. The token enters the queue and the customer receives its reference.
6. Compatible staff calls the token, provides service, and completes it.

### 8.3 Staff

1. Log in and select an available assigned counter.
2. View the counter's compatible queue and current service state.
3. Select `Call next`.
4. QueueWise atomically selects the next eligible token using priority, appointment rules, arrival time, compatibility, and starvation prevention.
5. Start service when the customer arrives.
6. Complete the token or skip it with a reason, then call the next token.

### 8.4 Admin

1. Log in.
2. Create a service and configure description, hours, capacity, duration, booking, priority, and notification rules.
3. Create counters and declare service compatibility.
4. Invite or create staff and assign roles, services, and counters.
5. View filtered analytics and audit history.

## 9. Functional Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-001 | Support customer registration with a unique phone or email and password. | Valid unused details create an account; duplicate contact details are rejected. |
| FR-002 | Support login and logout for customers, receptionists, staff, supervisors, and admins. | Valid credentials create the correct scoped session; invalid credentials return a safe error. |
| FR-003 | Allow users to select an organization and active service. | Only services available to the selected organization are shown. |
| FR-004 | Support appointment booking for available dates and slots. | A confirmed booking reserves one slot and shows a unique appointment and token reference. |
| FR-005 | Allow authorized receptionists to create walk-in tokens. | The token records customer, service, source, priority, and creation time. |
| FR-006 | Display token status, queue position, and estimated wait. | Waiting tokens show position; all token state changes update the view. |
| FR-007 | Allow staff to call the next eligible token for a selected counter. | The token becomes `CALLED` and cannot be called by another counter. |
| FR-008 | Allow staff to start, complete, and skip service. | Start creates `IN_SERVICE`; completion creates `COMPLETED`; skip requires a reason. |
| FR-009 | Provide a real-time operational queue view. | Token, call, completion, skip, cancellation, and pause changes appear within 5 seconds under normal load. |
| FR-010 | Support cancellation by customers or authorized staff according to policy. | A cancelled token leaves active counts and cannot be called. |
| FR-011 | Support no-show handling. | Staff can mark an overdue or called token `NO_SHOW` with actor, time, and reason. |
| FR-012 | Provide service, counter, staff, queue, and notification administration. | An admin can create, edit, deactivate, and view each supported configuration type. |
| FR-013 | Provide filtered operational analytics and export. | An authorized admin can filter a date range and service and export the displayed results. |
| FR-014 | Record security, configuration, and token lifecycle audit events. | Required events contain actor, timestamp, action, target, outcome, and reason where applicable. |
| FR-015 | Return actionable validation and error messages. | Rejected actions leave data unchanged and explain the next valid action. |

## 10. Non-Functional Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| NFR-001 | Enforce secure sessions and organization-scoped role authorization. | A user cannot access an endpoint or record outside the user's role and scope. |
| NFR-002 | Reflect queue state changes to active views within 5 seconds under normal load. | With 50 viewers and 10 events per minute, at least 95% of events arrive within 5 seconds. |
| NFR-003 | Preserve ordering and prevent duplicate active calls under concurrency. | Concurrent call-next requests produce one assignment and one deterministic queue state. |
| NFR-004 | Support current desktop and mobile browsers. | Core flows work from 360 px through 1440 px without horizontal scrolling or overlapping controls. |
| NFR-005 | Provide 99.5% monthly availability excluding planned maintenance. | Production monitoring reports at least 99.5% availability for the month. |
| NFR-006 | Use the organization's configured time zone consistently. | Token, appointment, notification, and audit timestamps use the configured time zone. |
| NFR-007 | Retain queue history and audit data for at least 12 months. | Required records remain available throughout the configured retention period. |
| NFR-008 | Keep common reads responsive. | 95% of token, queue, and service-list requests complete within 2 seconds under normal load. |
| NFR-009 | Support keyboard navigation and meaningful labels. | Keyboard-only users can complete core staff flows and automated checks find no critical accessibility violations. |
| NFR-010 | Avoid secrets and unnecessary personal data in logs. | Log review finds no plaintext password, session secret, or unnecessary sensitive value. |

## 11. User Roles

- **Customer:** Manages their own appointments and tokens.
- **Receptionist:** Registers customers and creates or manages walk-in tokens.
- **Staff:** Processes tokens at an assigned counter.
- **Supervisor:** Monitors assigned queues and handles authorized exceptions.
- **Admin:** Manages organization configuration, users, policies, analytics, and audit access.
- **System service:** Sends notifications and performs automated updates with a recorded system actor.

## 12. Role Permissions

| Capability | Customer | Receptionist | Staff | Supervisor | Admin |
|---|---:|---:|---:|---:|---:|
| Register/login | Own | Login | Login | Login | Login |
| Book own appointment | Yes | Optional on behalf | No | Optional | Optional |
| View token | Own | Assigned scope | Assigned counter | Assigned scope | Organization |
| Create walk-in token | No | Yes | No by default | Yes | Yes |
| Cancel token | Own/policy | Assigned scope | Assigned scope | Assigned scope | Yes |
| Call/start/complete | No | No by default | Assigned counter | Assigned scope | Emergency only |
| Skip/no-show | No | No by default | Assigned counter | Assigned scope | Yes |
| Configure services/counters | No | No | No | No by default | Yes |
| Assign staff | No | No | No | No by default | Yes |
| View analytics | Own status | Limited | Counter | Assigned scope | Organization |
| View audit | No | No | No | Limited | Yes |

Every permission is restricted to the user's organization and configured scope. Emergency admin actions require a reason and are audited.

## 13. Appointment Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-016 | Show slots based on service hours, capacity, existing bookings, and closures. | Closed, full, and out-of-hours slots cannot be selected. |
| FR-017 | Issue appointment confirmation with service, date, time, organization, and token. | Confirmation appears immediately and is available in the customer's appointment list. |
| FR-018 | Support cancellation until a configured cutoff. | Before cutoff succeeds; after cutoff is rejected without data change. |
| FR-019 | Handle late arrival and appointment expiry with configurable rules. | The appointment becomes `LATE` or `NO_SHOW` according to policy and does not silently retain priority. |
| FR-020 | Prevent disallowed overlapping appointments for one customer. | A conflicting booking is rejected and the existing appointment is unchanged. |

Appointments enter the operational queue according to the service's arrival-window and appointment policy. Booking alone does not imply physical arrival unless digital check-in is enabled.

## 14. Token Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-021 | Give every active appointment and walk-in a unique human-readable token reference. | No two active tokens share a reference within an organization and service date. |
| FR-022 | Store source, priority, timestamps, service, status, and eligible counters on a token. | Authorized token detail displays all required fields. |
| FR-023 | Support normal and urgent priority. | Authorized users can assign urgent priority and the event is audited. |
| FR-024 | Support `BOOKED`, `WAITING`, `CALLED`, `IN_SERVICE`, `COMPLETED`, `SKIPPED`, `NO_SHOW`, `CANCELLED`, and `EXPIRED`. | Each token has exactly one valid current state and valid transitions only. |
| FR-025 | Prevent concurrent duplicate processing. | Concurrent start or complete requests yield one successful transition. |
| FR-026 | Show numeric position or an explanation when position is not meaningful. | Terminal, called, and in-service states show a useful status explanation. |

Urgent priority is permission-controlled, intended for genuine urgent cases, and never bypasses the fairness rule indefinitely.

## 15. Queue Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-027 | Combine appointment and walk-in tokens using a documented ordering policy. | The queue shows the effective ordering key and deterministic input produces deterministic order. |
| FR-028 | Default ordering shall consider urgent priority, appointment windows, and arrival time. | A test set of urgent, on-time, late, and walk-in tokens produces the configured order. |
| FR-029 | Support multiple counters operating concurrently. | Compatible counters call different tokens without duplicate assignment. |
| FR-030 | Enforce counter compatibility. | A counter cannot call a token for an incompatible service. |
| FR-031 | Support paused counters. | A paused counter cannot call new tokens; active service remains visible. |
| FR-032 | Remove cancellations, no-shows, skips, and completions from active positions. | Counts and positions recalculate correctly after each terminal event. |
| FR-033 | Prevent normal-token starvation when urgent work repeatedly arrives. | A normal token past the configured fairness threshold is selected before additional urgent work unless an audited override exists. |
| FR-034 | Recalculate estimates after material queue changes. | Add, call, pause, complete, and skip events update affected estimates within 5 seconds under normal load. |
| FR-035 | Explain why no eligible token exists. | Staff see whether the queue is empty, incompatible, paused, or closed. |

### MVP queue policy

Tokens are partitioned by service and compatible counters. Urgent tokens are considered first, but a fairness threshold protects normal tokens. On-time appointments receive their configured preference within their arrival window. Walk-ins are ordered by arrival time after priority and appointment rules. Late appointments follow the configured late-arrival policy. Paused counters add no capacity, and queue selection is atomic.

## 16. Counter Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-036 | Allow admins to create, edit, activate, deactivate, and pause counters. | Status changes require permission and create audit events. |
| FR-037 | Require unique counter name or number within an organization and location. | Duplicate active identifiers are rejected. |
| FR-038 | Let staff select only an available assigned counter. | Unassigned, inactive, or occupied counters cannot be selected. |
| FR-039 | Show current token, elapsed service time, next eligible token, and pause state. | Each field is visible and updates after its related event. |
| FR-040 | Prevent a new call while a counter has an active token. | Call-next is disabled or rejected until completion, skip, or authorized override. |

## 17. Service Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-041 | Allow admins to create a service with name, description, active state, and duration estimate. | Required fields are validated and active services appear to customers. |
| FR-042 | Configure hours, booking horizon, slot capacity, grace period, and cancellation cutoff. | Saved settings affect generated slots and policy behavior. |
| FR-043 | Configure eligible counters and staff for each service. | Only compatible assigned counters and staff can process its tokens. |
| FR-044 | Deactivate services without deleting historical data. | Deactivated services accept no new bookings and retain historical records. |
| FR-045 | Store customer-facing instructions. | Instructions appear before confirmation and in appointment details. |

## 18. Notification Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-046 | Send booking confirmation through at least one configured channel. | A successful appointment creates a notification record and delivery status. |
| FR-047 | Support configurable appointment and expected-turn reminders. | Enabled reminders are scheduled and suppressed after cancellation or completion. |
| FR-048 | Notify customers when called or near their configured turn threshold. | Each configured event triggers at most one notification per token. |
| FR-049 | Record delivery status and retry transient failures. | Failure does not change token state and is retried or marked failed. |
| FR-050 | Support customer notification preferences. | Preferences affect future optional notifications without disabling required operational notices. |

MVP supports in-app status and one external channel, email or SMS depending on provider configuration. Provider failure must not block queue processing.

## 19. Analytics Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-051 | Report appointments, walk-ins, completions, cancellations, skips, no-shows, and active tokens. | Counts match filtered token records. |
| FR-052 | Report average and median wait and service time. | Metrics use documented timestamps and exclude incomplete records from duration calculations. |
| FR-053 | Support service, counter, source, priority, and date dimensions. | Each dimension can be filtered or grouped. |
| FR-054 | Report counter utilization as active service time versus available operating time. | The calculation labels incomplete periods and uses recorded counter sessions. |
| FR-055 | Export filtered analytics. | Export contains the displayed filters and no unauthorized records. |

## 20. Admin Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-056 | Manage organization profile, time zone, operating days, and closures. | A closure prevents new slots and is visible to booking users. |
| FR-057 | Invite, deactivate, reactivate, and scope staff. | Deactivated users cannot log in or process tokens; historical identity remains. |
| FR-058 | Configure queue, appointment, token, counter, and notification policies. | Invalid policy combinations are rejected before save. |
| FR-059 | Preview the effect of key configuration changes. | The UI summarizes affected hours, capacity, or compatibility before save. |
| FR-060 | Show notification and queue-processing health indicators. | Admins see current status or last successful processing time. |

## 21. Audit Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-061 | Audit login, logout, password reset, and permission events. | Each event has subject, timestamp, organization, outcome, and permitted source metadata. |
| FR-062 | Audit token creation, priority changes, calls, starts, completions, skips, no-shows, cancellations, and overrides. | Token history shows ordered lifecycle events with actor and reason where required. |
| FR-063 | Audit service, counter, staff assignment, policy, and closure changes. | Admins can identify who changed what and when. |
| FR-064 | Restrict audit records from ordinary editing and deletion. | Non-admins cannot edit; existing events cannot be silently altered. |
| FR-065 | Scope and filter audit searches. | Authorized users can filter by actor, action, target, date, and outcome within scope. |

## 22. Error Scenarios

| Scenario | Expected behavior | Acceptance criteria |
|---|---|---|
| Slot becomes unavailable during booking | Do not duplicate the booking; show alternatives. | At most one appointment owns the slot and retry is possible. |
| Connection is lost after confirmation | Make committed result recoverable. | Refreshing the appointment list shows the booking if it was committed. |
| Two staff call next simultaneously | Use one atomic assignment. | One token is assigned and the other request receives current state. |
| Notification provider is unavailable | Continue queue operation; retry or mark delivery failed. | Create, call, and complete actions still succeed. |
| Session expires during service | Require reauthentication without duplicate action. | A second completion cannot be created. |
| Counter pauses with waiting tokens | Stop new calls and retain visibility. | No call is possible until resume or authorized reassignment. |
| Invalid service configuration | Prevent save and identify the problem. | Last valid configuration remains active. |
| Unauthorized token change | Reject without mutation. | Safe error is returned and no false success audit event exists. |

## 23. Edge Cases

- Appointment and walk-in arrive simultaneously.
- Several urgent tokens arrive in rapid succession.
- A normal token reaches the starvation threshold during urgent work.
- Customer books, cancels, and tries to reuse a token reference.
- Customer arrives after the appointment grace period.
- Customer does not respond after being called.
- Called token's counter becomes paused or unavailable.
- Service has no compatible active counters.
- One counter is compatible with competing services.
- All compatible counters are paused or busy.
- Cancellation occurs while staff views the token.
- Connection fails immediately after call or completion.
- Organization changes time zone or closes unexpectedly.
- Notification retry happens after service completion.
- Daylight-saving transition affects an appointment.
- Customer submits booking twice.
- Historical records reference deactivated configuration.
- Analytics contains incomplete duration timestamps.
- One person has customer and staff identities.
- Demand exceeds daily capacity.

In every case, QueueWise must preserve one authoritative token state, avoid duplicate active work, and explain why normal processing is unavailable.

## 24. MVP Scope

### Included

- Customer registration, login, appointment list, booking, cancellation, and token status.
- Receptionist walk-in creation with normal and authorized urgent priority.
- Queue state transitions, appointments, walk-ins, no-shows, cancellations, skips, multiple counters, compatibility, pauses, and starvation prevention.
- Staff counter selection, call-next, start, complete, and skip.
- Admin service, counter, staff, assignment, hours, closure, policy, notification, analytics, and audit management.
- In-app updates and one external notification channel.
- Basic filtered analytics and export.
- Role authorization, input validation, centralized errors, responsive UI, and accessibility basics.

### Excluded

Native applications, payments, billing, insurance, medical records, multi-location enterprise administration, predictive staffing, hardware kiosk integration, chat, video service, and marketplace discovery.

## 25. Future Scope

1. Native mobile apps and push notifications.
2. Self-service kiosks and digital signage.
3. Multi-location administration.
4. Waitlist sharing and branch transfer.
5. WhatsApp or voice notification channels with consent controls.
6. Forecasting, staffing recommendations, and service-level alerts.
7. Customer feedback and service-quality workflows.
8. Payments, invoicing, memberships, and core-system integrations.
9. Configurable forms and document collection per service.
10. Controlled offline-tolerant staff mode.

## 26. Acceptance Criteria

The MVP is accepted when:

1. A customer can complete registration, login, service/date/time selection, booking, token monitoring, notification, and completion visibility.
2. A receptionist can create a walk-in token that enters the correct queue.
3. Staff can select a compatible counter, call next, start, complete, and skip with a reason.
4. An admin can create and configure a service, create a counter, assign staff, and view analytics.
5. Appointments and walk-ins follow the documented ordering policy.
6. Normal and urgent priority, no-shows, cancellations, multiple counters, compatibility, paused counters, and starvation prevention pass dedicated tests.
7. Concurrent actions do not duplicate processing or corrupt token state.
8. Every requirement in this document has test evidence or an approved exception.
9. Unauthorized users cannot perform restricted actions or view out-of-scope data.
10. Critical errors, notification failures, and invalid configurations do not corrupt queue state.
11. Core flows work on supported mobile and desktop widths and meet keyboard/accessibility criteria.
12. Production readiness checks meet availability, performance, logging, retention, and security targets.

## 27. Success Metrics

Measure during the first 90 days after launch and compare with baseline where available:

| Metric | Target |
|---|---|
| Appointment booking completion | At least 85% of started flows complete. |
| Walk-in creation time | Median receptionist creation time under 60 seconds. |
| Wait estimate usefulness | At least 70% of completed tokens have actual wait within 50% of estimate, excluding interruptions. |
| Lifecycle reliability | At least 99% of token transitions need no manual correction. |
| Duplicate call incidents | Fewer than 1 per 10,000 token transitions. |
| Notification delivery | At least 95% of eligible notifications reach the provider, excluding invalid destinations. |
| No-show reduction | 15% reduction from baseline after reminders are enabled. |
| Staff adoption | At least 80% of active service sessions use QueueWise for calls and completions. |
| Admin configuration time | Trained admin creates service, counter, and assignment in under 10 minutes. |
| Customer satisfaction | Average rating at least 4.0/5 where feedback is collected. |
| Availability and performance | Meet the non-functional availability and response-time targets. |

Metrics should be segmented by organization, service, source, priority, and date where privacy and volume permit. Metrics must not reward bypassing queue fairness or customer consent.
