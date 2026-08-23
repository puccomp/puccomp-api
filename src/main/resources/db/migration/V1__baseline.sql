create table accounts (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    status varchar(255) not null check (status in ('ACTIVE','DISABLED')),
    primary key (id)
);

create table tenants (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    name varchar(255) not null,
    slug varchar(255) not null unique,
    status varchar(255) not null check (status in ('ACTIVE','SUSPENDED')),
    primary key (id)
);

create table roles (
    active boolean not null,
    hierarchy_level integer not null,
    max_seats integer,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    description varchar(255) not null,
    name varchar(255) not null,
    primary key (id),
    constraint uk_roles_tenant_name unique (tenant_id, name)
);

create table departments (
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    lead_role_id uuid,
    tenant_id uuid not null,
    description varchar(255) not null,
    name varchar(255) not null,
    slug varchar(255) not null,
    primary key (id),
    constraint uk_departments_tenant_name unique (tenant_id, name),
    constraint uk_departments_tenant_slug unique (tenant_id, slug)
);

create table members (
    account_id uuid,
    department_id uuid,
    id uuid not null,
    role_id uuid,
    tenant_id uuid not null,
    course varchar(255) not null check (course in ('COMPUTER_SCIENCE','DATA_SCIENCE','SOFTWARE_ENGINEERING','COMPUTER_ENGINEERING','INFORMATION_SYSTEMS')),
    name varchar(255) not null,
    standing varchar(255) not null check (standing in ('OWNER','MEMBER')),
    status varchar(255) not null check (status in ('ACTIVE','ALUMNUS','INACTIVE','PENDING')),
    primary key (id)
);

create table invitations (
    accepted_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    revoked_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    created_by_account_id uuid not null,
    id uuid not null,
    role_id uuid,
    tenant_id uuid not null,
    email varchar(255) not null,
    standing varchar(255) not null check (standing in ('OWNER','MEMBER')),
    token_hash varchar(255) not null unique,
    token_prefix varchar(255) not null,
    primary key (id)
);

create table personal_access_tokens (
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone,
    last_used_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    account_id uuid not null,
    id uuid not null,
    tenant_id uuid not null,
    name varchar(255) not null,
    scopes varchar(255),
    token_hash varchar(255) not null unique,
    token_prefix varchar(255) not null,
    primary key (id)
);

create table role_permissions (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    role_id uuid not null,
    tenant_id uuid not null,
    permission varchar(255) not null check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE','ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE','PERMISSIONS_MANAGE')),
    primary key (id),
    constraint uk_role_permissions unique (tenant_id, role_id, permission)
);

create table member_permissions (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    member_id uuid not null,
    tenant_id uuid not null,
    permission varchar(255) not null check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE','ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE','PERMISSIONS_MANAGE')),
    primary key (id),
    constraint uk_member_permissions unique (tenant_id, member_id, permission)
);

alter table if exists departments
    add constraint fk_departments_lead_role
    foreign key (lead_role_id) references roles;

alter table if exists members
    add constraint fk_members_department
    foreign key (department_id) references departments;

alter table if exists members
    add constraint fk_members_role
    foreign key (role_id) references roles;
