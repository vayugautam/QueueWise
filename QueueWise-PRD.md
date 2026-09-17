# QueueWise Product Requirements Document

**Document status:** Draft
**Version:** 1.0
**Date:** 2026-09-17
**Product:** QueueWise

## 1. Product Overview

QueueWise is an appointment, token, and real-time queue management system for clinics, college offices, banks, salons, government counters, and other service businesses. It connects customers, receptionists, service staff, counters, and administrators in one workflow.

Customers can book appointments or receive walk-in tokens, see their position and estimated wait, and receive notifications. Receptionists can register walk-ins and manage customer flow. Staff can operate counters and process tokens. Administrators can configure services, counters, staff assignments, operating rules, and analytics.

The initial product is a responsive web application with a customer-facing experience and an authenticated staff/admin experience. The MVP prioritizes reliable token flow, clear queue state, and operational simplicity over advanced optimization.

## 2. Problem Statement

Service organizations commonly manage appointments and walk-ins through paper tickets, spreadsheets, verbal announcements, or disconnected tools. These methods create:

- Unclear wait times and crowded waiting areas.
- Duplicate, conflicting, or missed appointments.
- Poor visibility into whether a token is waiting, being served, skipped, or completed.
- Manual work for receptionists and staff.
- Uneven distribution of work across compatible counters.
- Little evidence for staffing and service-level decisions.
- Weak auditability when queue events are disputed.

QueueWise should make the current state of service delivery visible and actionable without requiring complex operational planning.

## 3. Product Vision

Make every service queue predictable, fair, and easy to operate by giving customers timely information and giving service teams a shared, real-time view of demand and capacity.

## 4. Goals

1. Let a customer book an appointment or obtain a walk-in token in under two minutes.
2. Give customers a reliable view of token status, queue position, and estimated wait.
3. Let staff call and complete tokens with a small number of clear actions.
4. Let receptionists handle walk-ins, cancellations, no-shows, and exceptions without breaking queue order.
5. Let administrators configure services, counters, staff, and basic operating rules without technical support.
6. Preserve an auditable history of important queue and account actions.
7. Provide basic analytics for volume, waiting time, service time, completion, cancellation, and no-show trends.

## 5. Non-Goals

The MVP will not:

- Process payments, insurance claims, or medical records.
- Replace a clinic, bank, college, salon, or government core system.
- Provide diagnosis, clinical decision support, or financial advice.
- Optimize staff schedules using predictive AI.
- Support complex multi-location enterprise tenancy in the first release.
- Guarantee an exact waiting time; estimates are explicitly approximate.
- Require native mobile applications; responsive web is sufficient for MVP.
- Provide video consultations or remote service delivery.
- Implement a marketplace or public directory of organizations.

## 6. Target Users

- Customers who need a scheduled or walk-in service.
- Receptionists or front-desk operators who register customers and manage arrivals.
- Service staff who process tokens at counters.
- Supervisors who monitor queues and intervene in exceptions.
- Administrators who configure the organization and review performance.

## 7. User Personas

### 7.1 Maya, Appointment Customer

Maya wants to reserve a clinic service during a lunch break. She needs a short booking flow, confirmation, reminders, and confidence that she will not lose her place.

### 7.2 Daniel, Walk-in Customer

Daniel arrives without an appointment. He wants a token quickly and needs to know whether he can wait elsewhere without missing his turn.

### 7.3 Priya, Receptionist

Priya manages arrivals and walk-ins while answering questions. She needs fast token creation, clear exception controls, and minimal typing.

### 7.4 Omar, Service Staff

Omar works at one of several counters. He needs to see only compatible work, call the next fair token, and record completion or skip reasons quickly.

### 7.5 Elena, Operations Admin

Elena configures services, operating hours, counters, staff, and reporting. She needs safe configuration changes and reliable operational data.

## 8. User Journeys

### 8.1 Customer Appointment Journey

1. Customer registers with name, phone or email, and password, or logs in.
2. Customer selects an organization and service.
3. QueueWise shows available dates and time slots based on service hours, capacity, existing appointments, and counter compatibility.
4. Customer selects a date and time and confirms the booking.
5. QueueWise creates an appointment and associated token, then displays a confirmation and sends a notification.
6. Customer opens the appointment view to see status, token number, queue position, and estimated wait.
7. QueueWise sends configured reminders and a near-turn notification.
8. Staff calls the token; the customer sees the called status.
9. Staff starts service; the customer sees that service is in progress.
10. Staff completes service; QueueWise marks the token completed and shows the completion time.

### 8.2 Walk-in Journey

1. Customer approaches the receptionist or service desk.
2. Receptionist identifies or creates the customer record when required.
3. Receptionist selects a service and optionally marks the request urgent when authorized.
4. QueueWise creates a walk-in token with its arrival time, priority, service, and eligible counters.
5. The token enters the appropriate queue and the customer receives a printed, displayed, or message-based token reference.
6. QueueWise updates the token position and estimated wait as the queue changes.
7. A compatible staff member calls the token.
8. Staff starts and completes the service, or skips the token with a reason.

### 8.3 Staff Journey

1. Staff logs in.
2. Staff selects an available assigned counter.
3. QueueWise displays the counter's compatible waiting queue and current service state.
4. Staff selects `Call next`; QueueWise chooses the next eligible token using priority, arrival/appointment rules, compatibility, and starvation prevention.
5. Staff starts service when the customer arrives.
6. Staff completes the token, skips it with a required reason, or returns it to the queue only when permitted by policy.
7. Staff calls the next token.

### 8.4 Admin Journey

1. Admin logs in.
2. Admin creates a service with name, description, duration estimate, operating hours, booking rules, priority policy, and notification settings.
3. Admin configures or edits the service and reviews validation warnings.
4. Admin creates counters and declares which services each counter can handle.
5. Admin creates or invites staff and assigns services, locations, and counters.
6. Admin views analytics by date, service, counter, source, priority, and outcome.
7. Admin reviews audit history for configuration and operational changes.

## 9. Functional Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-001 | The system shall support customer registration with a unique phone number or email and password. | Given valid unused details, an account is created; duplicate contact details are rejected with a clear message. |
| FR-002 | The system shall support login and logout for customers, staff, receptionists, supervisors, and admins. | Valid credentials create a session with the correct role; invalid credentials do not reveal which field was wrong. |
| FR-003 | The system shall allow an authorized user to select an organization and service. | Only active services available to the selected organization are shown. |
| FR-004 | The system shall support appointment booking for available dates and time slots. | A confirmed booking reserves one slot and displays a unique appointment and token reference. |
| FR-005 | The system shall support walk-in token creation by an authorized receptionist. | A receptionist can create a token with customer, service, source, priority, and creation time. |
| FR-006 | The system shall display token status, queue position, and estimated wait. | Status and position update after a relevant queue event without requiring a new token. |
| FR-007 | The system shall allow staff to call the next eligible token for a selected counter. | The selected token changes to `CALLED`, is visible to the customer, and cannot be called by another counter simultaneously. |
| FR-008 | The system shall allow staff to start, complete, and skip service. | Start changes status to `IN_SERVICE`; complete changes it to `COMPLETED`; skip requires an allowed reason. |
| FR-009 | The system shall provide a real-time operational queue view for authorized staff. | New tokens, calls, completions, skips, cancellations, and pauses appear in the view within 5 seconds under normal operating load. |
| FR-010 | The system shall support cancellation by the customer or authorized staff according to policy. | A cancellable token changes to `CANCELLED`, is removed from active queue counts, and cannot be called. |
| FR-011 | The system shall support no-show handling. | Staff can mark a called or overdue token `NO_SHOW` with a timestamp and reason; the event is auditable. |
| FR-012 | The system shall support service, counter, staff, notification, and queue configuration by admins. | An admin can create, edit, deactivate, and view each configuration type subject to validation. |
| FR-013 | The system shall provide operational analytics and exportable filtered results. | An authorized admin can filter a date range and service and see counts and duration metrics defined in Section 19. |
| FR-014 | The system shall record audit events for security, configuration, and token lifecycle changes. | Each required event includes actor, timestamp, action, target, and outcome as defined in Section 21. |
| FR-015 | The system shall show actionable validation and error messages without exposing sensitive data. | Each rejected action leaves data unchanged and explains the next valid action. |

## 10. Non-Functional Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| NFR-001 | The application shall protect authenticated data using secure session handling and role-based authorization. | A user cannot access an endpoint or record outside their role and organization; unauthorized requests return a controlled error. |
| NFR-002 | Queue state changes shall be reflected to other active views within 5 seconds under normal operating load. | In a test with 50 active queue viewers and 10 events per minute, 95% of events appear within 5 seconds. |
| NFR-003 | The system shall preserve token ordering and prevent duplicate active calls. | Concurrent call-next requests result in one token assignment only, and the queue has one deterministic next state. |
| NFR-004 | The core customer booking and staff queue screens shall be usable on current desktop and mobile browsers. | The MVP flows complete at viewport widths from 360 px through 1440 px without horizontal scrolling or overlapping primary controls. |
| NFR-005 | The service shall provide 99.5% monthly availability excluding planned maintenance communicated in advance. | Monitoring reports at least 99.5% availability for the production month. |
| NFR-006 | The system shall provide clear timestamps using the organization's configured time zone. | Token, appointment, notification, and audit timestamps display consistently in the configured time zone. |
| NFR-007 | The system shall retain queue history and audit data for at least 12 months in MVP. | Records older than 12 months are not deleted before the configured retention job or administrator review. |
| NFR-008 | Common reads shall respond promptly. | 95% of token status, queue view, and service list requests complete within 2 seconds under normal load. |
| NFR-009 | The system shall support keyboard navigation and meaningful labels for primary controls. | A keyboard-only tester can complete login, token lookup, call-next, start, complete, and skip; automated accessibility checks have no critical violations. |
| NFR-010 | The system shall avoid exposing passwords, authentication tokens, or unnecessary personal data in logs. | A log review of authentication and token flows contains no plaintext password or session secret. |

## 11. User Roles

- **Customer:** Books and monitors their own appointments and tokens.
- **Receptionist:** Creates and manages walk-in tokens and customer arrival actions.
- **Staff:** Operates an assigned counter and processes eligible tokens.
- **Supervisor:** Monitors queues and performs authorized exception actions across assigned services or counters.
- **Admin:** Configures the organization, users, services, counters, policies, analytics, and audit access.
- **System service:** Sends notifications, updates estimates, and records automated events; it cannot perform human-authorized actions without a recorded system actor.

## 12. Role Permissions

| Capability | Customer | Receptionist | Staff | Supervisor | Admin |
|---|---:|---:|---:|---:|---:|
| Register/login | Own | Login | Login | Login | Login |
| Book own appointment | Yes | On behalf of customer if enabled | No | On behalf if enabled | On behalf if enabled |
| View own token | Yes | Assigned service | Assigned counter | Assigned scope | Organization |
| Create walk-in token | No | Yes | Optional, disabled by default | Yes | Yes |
| Cancel own token | Policy-based | Yes | Assigned scope | Assigned scope | Yes |
| Call/start/complete token | No | No by default | Assigned counter | Assigned scope | Emergency only, audited |
| Skip/no-show token | No | No by default | Assigned counter | Assigned scope | Yes, audited |
| Configure services/counters | No | No | No | No by default | Yes |
| Assign staff | No | No | No | No by default | Yes |
| View analytics | Own status only | Operational limited | Counter limited | Assigned scope | Organization |
| View audit records | No | No | No | Limited operational | Yes |

All permissions are restricted to the user's organization and configured scope. Admin emergency actions require a reason and are prominently audited.

## 13. Appointment Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-016 | The system shall show only bookable slots based on service hours, configured capacity, existing bookings, and closures. | A slot that is outside hours, full, or closed cannot be selected. |
| FR-017 | The system shall issue an appointment confirmation containing service, date, time, organization, and token reference. | Confirmation is shown immediately and retrievable from the customer's appointment list. |
| FR-018 | The system shall support appointment cancellation until the configured cutoff. | Before cutoff cancellation succeeds; after cutoff the customer receives the configured policy message and no data changes. |
| FR-019 | The system shall handle late arrival and appointment expiry using configurable rules. | An appointment past its grace period is marked `LATE` or `NO_SHOW` according to policy and cannot silently retain priority. |
| FR-020 | The system shall prevent duplicate overlapping appointments for the same customer and service where policy disallows them. | A conflicting booking is rejected and the existing appointment remains unchanged. |

Appointments enter the operational queue according to the service's configured arrival window and appointment policy. An appointment is not automatically treated as physically present until check-in, receptionist confirmation, or an explicitly configured digital check-in action.

## 14. Token Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-021 | Every active appointment and walk-in shall have a unique human-readable token reference within its organization and service date. | Two active tokens cannot share the same reference for the same service date. |
| FR-022 | A token shall include source, priority, creation/check-in time, service, status, and eligible counters. | The token detail view displays all required fields to authorized users. |
| FR-023 | The system shall support normal and urgent priority. | An authorized user can assign urgent priority; the event and actor are recorded. |
| FR-024 | The system shall support token states `BOOKED`, `WAITING`, `CALLED`, `IN_SERVICE`, `COMPLETED`, `SKIPPED`, `NO_SHOW`, `CANCELLED`, and `EXPIRED`. | Every token has exactly one current state and only configured transitions are allowed. |
| FR-025 | The system shall prevent a token from being processed twice concurrently. | Concurrent start or complete requests produce one successful state transition and a controlled response for the other request. |
| FR-026 | The system shall show a token's current position or an explanatory state when position is not meaningful. | A waiting token has a numeric position; a called, completed, cancelled, or no-show token has a status explanation instead. |

Urgent priority is intended for genuinely urgent operational cases, not indefinite bypassing. Its use must be permission-controlled and auditable.

## 15. Queue Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-027 | The queue shall combine eligible appointments and walk-ins using a documented ordering policy. | For each service, the queue view shows the effective ordering key; the same inputs produce the same next token. |
| FR-028 | The default ordering policy shall prioritize urgent tokens, then honor appointment windows, then use arrival time, while allowing configured policy variations. | A test set containing urgent, on-time appointment, late appointment, and walk-in tokens produces the configured order. |
| FR-029 | The queue shall support multiple counters operating concurrently. | Two compatible counters can call different eligible tokens without duplicate assignment. |
| FR-030 | The queue shall enforce counter compatibility. | A counter cannot call a token for a service it is not configured to handle. |
| FR-031 | The queue shall support paused counters. | A paused counter cannot call new tokens; its active service remains visible and an authorized supervisor can resume or reassign according to policy. |
| FR-032 | The queue shall handle cancellations, no-shows, skips, and completions without leaving phantom active positions. | After each terminal event, active counts and positions recalculate correctly. |
| FR-033 | The queue shall prevent starvation of normal tokens when urgent tokens are repeatedly added. | Under the configured fairness rule, a normal token that remains eligible for the starvation threshold is selected before additional urgent work unless a supervisor override is recorded. |
| FR-034 | The queue shall recalculate estimated waits after material queue changes. | Adding, calling, pausing, completing, or skipping a token updates affected estimates within 5 seconds under normal operating load. |
| FR-035 | The queue shall provide a visible explanation when no eligible token exists. | Staff see whether the queue is empty, all tokens are incompatible, all tokens are paused, or service is closed. |

### Queue policy for MVP

- Tokens are partitioned by service and eligible counters.
- Urgent tokens are considered first, but a configurable fairness threshold prevents normal-token starvation.
- On-time appointments receive their configured appointment preference within a reasonable arrival window.
- Walk-ins are ordered by arrival time after applicable priority and appointment rules.
- A late appointment follows the configured late-arrival policy and does not silently displace already-called work.
- A paused counter contributes no new capacity; tokens remain available to other compatible counters.
- Queue selection is atomic so two staff members cannot receive the same token.

## 16. Counter Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-036 | Admins shall create, edit, activate, deactivate, and pause counters. | Counter status changes are available only to authorized users and are audited. |
| FR-037 | A counter shall have a unique name or number within its organization and location. | Duplicate active counter identifiers are rejected. |
| FR-038 | A staff member shall select only an available counter assigned to them. | Unassigned, inactive, or already exclusively occupied counters cannot be selected. |
| FR-039 | The counter view shall show current token, elapsed service time, next eligible token, and pause state. | Each field is visible and updates after the related event. |
| FR-040 | The system shall prevent new calls while a counter has an active token unless an authorized override is used. | Call-next is disabled or rejected while the counter is `IN_SERVICE`. |

## 17. Service Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-041 | Admins shall create a service with name, description, active state, and estimated duration. | Required fields are validated and the service appears in the service list when active. |
| FR-042 | Admins shall configure service hours, booking horizon, slot capacity, grace period, and cancellation cutoff. | A saved configuration changes slot generation and policy behavior in a test scenario. |
| FR-043 | Admins shall configure which counters and staff are eligible for a service. | A token for the service is visible only to compatible assigned counters and authorized staff. |
| FR-044 | Services shall be deactivatable without deleting historical appointments or tokens. | A deactivated service cannot receive new bookings but historical records remain viewable. |
| FR-045 | The system shall support a service description and customer-facing instructions. | Instructions appear before confirmation and in the appointment detail view. |

## 18. Notification Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-046 | The system shall send a booking confirmation through at least one configured channel. | A successful appointment produces a notification record with status and destination. |
| FR-047 | The system shall support configurable reminders before an appointment or expected turn. | An enabled reminder is scheduled and suppressed when the token is cancelled or completed. |
| FR-048 | The system shall notify a customer when their token is called or approaches the configured threshold. | The notification is triggered once per configured event and the event is visible in notification history. |
| FR-049 | The system shall show notification delivery status and retry transient failures. | Failed delivery is marked failed or retried according to policy; it does not change token state. |
| FR-050 | Customers shall be able to manage permitted notification preferences. | A preference change affects future notifications and does not alter required operational notices unless policy allows it. |

MVP notification channels: in-app status and email or SMS, depending on configured provider availability. A provider outage must not block token creation or queue processing.

## 19. Analytics Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-051 | The system shall report total appointments, walk-ins, completed tokens, cancelled tokens, skipped tokens, no-shows, and active tokens. | Counts match the filtered token records for the selected period. |
| FR-052 | The system shall report average and median wait time and service time. | Metrics use documented start/end timestamps and exclude records without the required timestamps. |
| FR-053 | The system shall report results by service, counter, source, priority, and date. | Each supported dimension can be selected as a filter or grouping in the analytics view. |
| FR-054 | The system shall report counter utilization for active service time versus available operating time. | Utilization is calculated from recorded counter sessions and clearly labels incomplete periods. |
| FR-055 | The system shall allow authorized users to export filtered analytics. | Export contains the same filter period and dimensions shown on screen and excludes unauthorized records. |

## 20. Admin Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-056 | Admins shall manage organization profile, time zone, operating days, and closure dates. | A saved closure prevents new slots and is shown to users attempting to book. |
| FR-057 | Admins shall invite, deactivate, reactivate, and assign staff roles and scopes. | A deactivated user cannot log in or process tokens; historical actions retain the former user's identity. |
| FR-058 | Admins shall configure queue, appointment, token, counter, and notification policies within supported bounds. | Invalid combinations are rejected with a clear reason before saving. |
| FR-059 | Admins shall preview the operational effect of key configuration changes. | Before saving service hours, capacity, or compatibility changes, the UI displays the affected setting summary. |
| FR-060 | Admins shall view system health indicators for notification and queue update processing. | The admin view reports current status or last successful processing time for each integration. |

## 21. Audit Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-061 | The system shall audit login success, login failure threshold events, logout, password reset, and permission changes. | Each event records actor or subject, timestamp, organization, outcome, and source metadata permitted by policy. |
| FR-062 | The system shall audit appointment and token creation, priority changes, calls, starts, completions, skips, no-shows, cancellations, and overrides. | A token history shows the ordered lifecycle events with actor and reason where applicable. |
| FR-063 | The system shall audit service, counter, staff assignment, policy, and closure changes. | An admin can identify who changed the configuration, what changed, and when. |
| FR-064 | Audit records shall be append-oriented and restricted from ordinary editing or deletion. | A non-admin cannot edit audit records; an admin cannot silently alter an existing event. |
| FR-065 | Audit searches shall be permission-scoped and filterable. | An authorized user can filter by actor, action, target, date, and outcome within their scope. |

## 22. Error Scenarios

| Scenario | Expected behavior | Acceptance criteria |
|---|---|---|
| Slot becomes unavailable during booking | Do not create a duplicate booking; show alternative slots. | The original slot remains occupied by at most one appointment and the customer can retry. |
| Customer loses connection after confirmation | Booking result is recoverable. | Refreshing the appointment list shows the confirmed booking if it was committed. |
| Two staff members call next simultaneously | One atomic assignment wins. | Only one token is assigned and the other user gets a current-state response. |
| Notification provider is unavailable | Queue operation continues and delivery is retried or marked failed. | Token creation, calling, and completion succeed independently of provider status. |
| Staff session expires during service | Protect the action and preserve current state. | The user must reauthenticate; a duplicate completion cannot be created. |
| Counter is paused with a waiting queue | Stop new calls and retain queue visibility. | The counter cannot call new work until resumed or an authorized reassignment occurs. |
| Invalid service configuration | Prevent save. | The UI identifies the invalid field or rule and retains the last valid configuration. |
| Customer requests an unauthorized token change | Reject without data mutation. | The customer sees a safe error and the audit log has no false success event. |

## 23. Edge Cases

- An appointment and walk-in arrive at the same time.
- Multiple urgent tokens are created in rapid succession.
- A normal token reaches the starvation threshold while urgent work continues.
- A customer books, cancels, and attempts to reuse the same token reference.
- A customer arrives after the appointment grace period.
- A customer does not respond after being called.
- A token is called, then the counter becomes paused or unavailable.
- A service has no compatible active counters.
- A counter is compatible with multiple services that have competing queues.
- All compatible counters are paused or in service.
- A token is cancelled while a staff member is viewing it.
- A staff member loses connection immediately after calling or completing a token.
- The organization changes its time zone or closes unexpectedly.
- A notification is retried after the customer has already completed service.
- An appointment spans midnight in a time zone with daylight-saving changes.
- A customer submits the booking form twice.
- Historical records refer to a deactivated service, counter, or staff member.
- Analytics includes tokens with missing or invalid duration timestamps.
- The same person has customer and staff identities in one organization.
- A queue has more demand than its configured daily capacity.

For all edge cases, the system must preserve a single authoritative token state, avoid duplicate active work, and provide a visible explanation when normal processing is not possible.

## 24. MVP Scope

### Included in MVP

- Customer registration, login, profile, appointment list, and token status.
- Appointment booking, cancellation, confirmation, and configurable grace/cutoff rules.
- Receptionist walk-in token creation.
- Normal and urgent priority with auditable changes.
- Queue states, multiple counters, counter compatibility, pauses, no-shows, cancellations, skips, and starvation prevention.
- Staff counter selection, call next, start, complete, and skip.
- Admin management of services, service rules, counters, staff, assignments, organization hours, closures, and notifications.
- In-app queue updates and one external notification channel, email or SMS.
- Basic analytics and CSV-style export.
- Role-based authorization and audit history.
- Responsive web interface and basic accessibility support.

### Explicitly excluded from MVP

- Native mobile apps.
- Payments, billing, insurance, and medical records.
- Multi-location enterprise administration.
- Advanced forecasting, AI prioritization, and automatic staff scheduling.
- Public kiosks and hardware integrations beyond simple printable/displayable token output.
- Two-way chat, video service, and marketplace discovery.

## 25. Future Scope

Prioritize only after MVP usage validates the core queue flow:

1. Native customer and staff mobile applications with push notifications.
2. Self-service kiosk and digital signage integrations.
3. Multi-location organizations with centralized administration.
4. Waitlist sharing, transfer to another branch, and customer arrival geofencing.
5. More notification channels such as WhatsApp or voice calls, subject to provider and consent requirements.
6. Advanced forecasting, staffing recommendations, and service-level alerts.
7. Customer feedback, ratings, and service quality workflows.
8. Payments, invoicing, memberships, and integrations with clinic, bank, college, or salon systems.
9. Configurable workflow forms and document collection per service.
10. Offline-tolerant staff mode for controlled network outages.

## 26. Acceptance Criteria

The MVP is accepted when all of the following are true:

1. A new customer can register, log in, select a service/date/time, book a token, receive confirmation, monitor status, receive a near-turn or called notification, and see completion.
2. A receptionist can create a walk-in token, assign normal or authorized urgent priority, and see it enter the correct service queue.
3. A staff member can select a compatible counter, call the next eligible token, start service, complete it, or skip it with a reason.
4. An admin can create and configure a service, create a counter, assign compatible services and staff, and view filtered analytics.
5. Appointment and walk-in tokens coexist according to the documented ordering policy.
6. Normal priority, urgent priority, no-shows, cancellations, multiple counters, counter compatibility, paused counters, and starvation prevention are demonstrated in test scenarios.
7. Concurrent call-next and completion actions do not create duplicate processing or inconsistent token state.
8. Every functional and applicable non-functional requirement in this document has test evidence or an approved exception.
9. Unauthorized users cannot perform restricted actions or view out-of-scope data.
10. Critical errors, notification failures, and configuration validation failures are handled without corrupting queue state.
11. Core flows work at supported mobile and desktop widths and meet the documented keyboard navigation and accessibility criteria.
12. Production readiness checks confirm the availability, performance, logging, retention, and security targets in Section 10.

## 27. Success Metrics

Measure for the first 90 days after MVP launch, compared with the organization's baseline where available:

| Metric | Target |
|---|---|
| Appointment booking completion rate | At least 85% of started booking flows complete successfully. |
| Walk-in token creation time | Median receptionist creation time under 60 seconds. |
| Customer wait estimate usefulness | At least 70% of completed tokens have actual wait within 50% of the estimate, excluding operational interruptions. |
| Queue processing reliability | At least 99% of token lifecycle transitions complete without manual data correction. |
| Duplicate active token/call incidents | Fewer than 1 per 10,000 token transitions. |
| Notification delivery | At least 95% of eligible notifications reach the provider successfully, excluding invalid destinations. |
| No-show rate | Reduce no-shows by 15% from baseline after reminders are enabled. |
| Staff adoption | At least 80% of active service sessions use QueueWise for call and completion events. |
| Admin time to configure | A trained admin can create a service, counter, and staff assignment in under 10 minutes. |
| Customer satisfaction | Average post-service rating of at least 4.0/5 where feedback is collected. |
| Availability | Meet the monthly availability target defined in the non-functional requirements. |
| Performance | Meet the queue update and common-read response targets at the 95th percentile. |

Metrics must be segmented by organization, service, source, priority, and date where privacy and volume permit. Success metrics are used to decide whether to expand future scope, not to justify bypassing queue fairness or customer consent.
