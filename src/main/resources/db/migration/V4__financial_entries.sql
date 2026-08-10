create table financial_entries (
    occurred_on date not null,
    amount numeric(14, 2) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    category varchar(120) not null,
    description varchar(255) not null,
    receipt_url varchar(500),
    type varchar(20) not null check (type in ('INCOME','EXPENSE')),
    primary key (id),
    constraint ck_financial_entries_amount_positive check (amount > 0)
);

create index ix_financial_entries_tenant_occurred_on
    on financial_entries (tenant_id, occurred_on desc);

create index ix_financial_entries_tenant_type_occurred_on
    on financial_entries (tenant_id, type, occurred_on desc);

-- Registra FINANCIAL_READ e FINANCIAL_WRITE nas check constraints das tabelas de permissão.
-- OWNER/ADMIN já recebem todas as permissões pelo catálogo em código.

alter table role_permissions drop constraint role_permissions_permission_check;
alter table role_permissions add constraint role_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'PERMISSIONS_MANAGE','COURSES_WRITE','FINANCIAL_READ','FINANCIAL_WRITE'));

alter table member_permissions drop constraint member_permissions_permission_check;
alter table member_permissions add constraint member_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'PERMISSIONS_MANAGE','COURSES_WRITE','FINANCIAL_READ','FINANCIAL_WRITE'));
