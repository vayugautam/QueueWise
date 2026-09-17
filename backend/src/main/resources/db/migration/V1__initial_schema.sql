create extension if not exists pgcrypto;

create table organizations (
    id uuid primary key default gen_random_uuid(),
    name varchar(160) not null,
    slug varchar(100) not null unique,
    time_zone varchar(64) not null default 'UTC',
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_organizations_status check (status in ('ACTIVE', 'INACTIVE'))
);

create table roles (
    id uuid primary key default gen_random_uuid(),
    code varchar(40) not null unique,
    description varchar(255) not null
);

create table users (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    email varchar(254),
    phone varchar(32),
    password_hash varchar(255) not null,
    first_name varchar(80) not null,
    last_name varchar(80) not null,
    status varchar(20) not null default 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint ck_users_contact check (email is not null or phone is not null),
    constraint ck_users_status check (status in ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

create unique index uq_users_org_email on users (organization_id, lower(email)) where email is not null;
create unique index uq_users_org_phone on users (organization_id, phone) where phone is not null;
create index ix_users_org_status on users (organization_id, status);

create table user_roles (
    user_id uuid not null references users(id),
    role_id uuid not null references roles(id),
    assigned_at timestamptz not null default now(),
    assigned_by uuid references users(id),
    primary key (user_id, role_id)
);
create index ix_user_roles_role_user on user_roles (role_id, user_id);

create table services (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    name varchar(160) not null,
    description text,
    instructions text,
    status varchar(20) not null default 'ACTIVE',
    estimated_duration_seconds integer not null,
    booking_horizon_days smallint not null default 30,
    slot_capacity smallint not null default 1,
    grace_period_minutes smallint not null default 15,
    cancellation_cutoff_minutes integer not null default 60,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint ck_services_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_services_duration check (estimated_duration_seconds > 0),
    constraint ck_services_capacity check (slot_capacity > 0),
    constraint ck_services_horizon check (booking_horizon_days >= 0),
    constraint ck_services_grace check (grace_period_minutes >= 0),
    constraint ck_services_cutoff check (cancellation_cutoff_minutes >= 0)
);
create unique index uq_services_org_name on services (organization_id, lower(name));
create index ix_services_org_status on services (organization_id, status);

create table service_hours (
    service_id uuid not null references services(id),
    day_of_week smallint not null,
    opens_at time not null,
    closes_at time not null,
    is_closed boolean not null default false,
    primary key (service_id, day_of_week),
    constraint ck_service_hours_day check (day_of_week between 1 and 7),
    constraint ck_service_hours_range check (is_closed or closes_at > opens_at)
);

create table counters (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    name varchar(80) not null,
    location_label varchar(120),
    status varchar(20) not null default 'AVAILABLE',
    pause_reason varchar(255),
    current_staff_id uuid references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint ck_counters_status check (status in ('INACTIVE', 'AVAILABLE', 'IN_USE', 'PAUSED')),
    constraint ck_counters_pause_reason check (status <> 'PAUSED' or pause_reason is not null)
);
create unique index uq_counters_org_name on counters (organization_id, lower(name));
create index ix_counters_org_status on counters (organization_id, status);

create table counter_services (
    counter_id uuid not null references counters(id),
    service_id uuid not null references services(id),
    created_at timestamptz not null default now(),
    primary key (counter_id, service_id)
);
create index ix_counter_services_service_counter on counter_services (service_id, counter_id);

create table staff_services (
    user_id uuid not null references users(id),
    service_id uuid not null references services(id),
    assigned_at timestamptz not null default now(),
    assigned_by uuid references users(id),
    primary key (user_id, service_id)
);

create table staff_counters (
    user_id uuid not null references users(id),
    counter_id uuid not null references counters(id),
    assigned_at timestamptz not null default now(),
    assigned_by uuid references users(id),
    primary key (user_id, counter_id)
);

create table queues (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    service_id uuid not null references services(id),
    business_date date not null,
    status varchar(20) not null default 'OPEN',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    next_token_number integer not null default 0,
    constraint uq_queues_org_service_date unique (organization_id, service_id, business_date),
    constraint ck_queues_status check (status in ('OPEN', 'PAUSED', 'CLOSED')),
    constraint ck_queues_next_number check (next_token_number >= 0)
);
create index ix_queues_org_date_status on queues (organization_id, business_date, status);

create table appointments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    customer_id uuid not null references users(id),
    service_id uuid not null references services(id),
    queue_id uuid not null references queues(id),
    scheduled_start timestamptz not null,
    scheduled_end timestamptz not null,
    status varchar(20) not null default 'BOOKED',
    confirmation_code varchar(40) not null,
    cancelled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint uq_appointments_confirmation unique (organization_id, confirmation_code),
    constraint ck_appointments_range check (scheduled_end > scheduled_start),
    constraint ck_appointments_status check (status in ('BOOKED', 'CHECKED_IN', 'LATE', 'CANCELLED', 'NO_SHOW', 'COMPLETED'))
);
create index ix_appointments_customer_history on appointments (organization_id, customer_id, scheduled_start desc);
create index ix_appointments_service_availability on appointments (organization_id, service_id, scheduled_start, status);
create index ix_appointments_queue_status_time on appointments (queue_id, status, scheduled_start);

create table tokens (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    queue_id uuid not null references queues(id),
    service_id uuid not null references services(id),
    customer_id uuid not null references users(id),
    appointment_id uuid unique references appointments(id),
    assigned_counter_id uuid references counters(id),
    token_number integer not null,
    display_code varchar(30) not null,
    source varchar(20) not null,
    priority varchar(20) not null default 'NORMAL',
    status varchar(20) not null default 'WAITING',
    arrival_at timestamptz,
    called_at timestamptz,
    service_started_at timestamptz,
    service_completed_at timestamptz,
    terminal_reason varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint uq_tokens_queue_number unique (queue_id, token_number),
    constraint uq_tokens_queue_code unique (queue_id, display_code),
    constraint ck_tokens_source check (source in ('APPOINTMENT', 'WALK_IN')),
    constraint ck_tokens_priority check (priority in ('NORMAL', 'URGENT')),
    constraint ck_tokens_status check (status in ('WAITING', 'CALLED', 'SERVING', 'COMPLETED', 'SKIPPED', 'NO_SHOW', 'CANCELLED'))
);
create index ix_tokens_queue_waiting_order on tokens (queue_id, status, priority, arrival_at, id);
create index ix_tokens_customer_history on tokens (organization_id, customer_id, created_at desc);
create index ix_tokens_org_created_at on tokens (organization_id, created_at);
create index ix_tokens_org_arrival_at on tokens (organization_id, arrival_at);
create index ix_tokens_counter_active on tokens (assigned_counter_id, status);
create index ix_tokens_service_status_arrival on tokens (service_id, status, arrival_at);

create table token_events (
    id bigint generated by default as identity primary key,
    token_id uuid not null references tokens(id),
    organization_id uuid not null references organizations(id),
    actor_user_id uuid references users(id),
    event_type varchar(40) not null,
    from_status varchar(20),
    to_status varchar(20),
    reason varchar(255),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index ix_token_events_token_time on token_events (token_id, created_at);
create index ix_token_events_org_type_time on token_events (organization_id, event_type, created_at);

create table notifications (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    recipient_user_id uuid not null references users(id),
    token_id uuid references tokens(id),
    appointment_id uuid references appointments(id),
    channel varchar(20) not null,
    notification_type varchar(40) not null,
    status varchar(20) not null default 'PENDING',
    provider_reference varchar(160),
    attempt_count smallint not null default 0,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    last_error_code varchar(80),
    created_at timestamptz not null default now(),
    constraint ck_notifications_channel check (channel in ('IN_APP', 'EMAIL', 'SMS')),
    constraint ck_notifications_status check (status in ('PENDING', 'SENT', 'FAILED', 'CANCELLED'))
);
create index ix_notifications_retry on notifications (status, next_attempt_at);

create table counter_sessions (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id),
    counter_id uuid not null references counters(id),
    staff_user_id uuid not null references users(id),
    started_at timestamptz not null,
    ended_at timestamptz,
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    constraint ck_counter_sessions_range check (ended_at is null or ended_at > started_at)
);
create unique index uq_counter_active_session on counter_sessions (counter_id) where ended_at is null;
create index ix_counter_sessions_org_start on counter_sessions (organization_id, started_at);

create table audit_logs (
    id bigint generated by default as identity primary key,
    organization_id uuid not null references organizations(id),
    actor_user_id uuid references users(id),
    action varchar(80) not null,
    entity_type varchar(60) not null,
    entity_id uuid,
    outcome varchar(20) not null,
    reason varchar(255),
    before_data jsonb,
    after_data jsonb,
    correlation_id varchar(64),
    ip_address varchar(45),
    created_at timestamptz not null default now(),
    constraint ck_audit_outcome check (outcome in ('SUCCESS', 'FAILURE'))
);
create index ix_audit_logs_org_time on audit_logs (organization_id, created_at desc);
create index ix_audit_logs_entity on audit_logs (organization_id, entity_type, entity_id, created_at);
create index ix_audit_logs_actor_time on audit_logs (organization_id, actor_user_id, created_at desc);
create index ix_audit_logs_correlation on audit_logs (correlation_id);

insert into roles (code, description) values
    ('CUSTOMER', 'Customer account'),
    ('RECEPTIONIST', 'Front desk operator'),
    ('STAFF', 'Service counter staff'),
    ('SUPERVISOR', 'Queue supervisor'),
    ('ADMIN', 'Organization administrator')
on conflict (code) do nothing;
