create table selection_processes (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    start_date timestamp(6) with time zone,
    end_date timestamp(6) with time zone,
    id uuid not null,
    tenant_id uuid not null,
    title varchar(255) not null,
    description text,
    status varchar(255) not null check (status in ('DRAFT','OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    primary key (id)
);

alter table role_permissions drop constraint role_permissions_permission_check;
alter table role_permissions add constraint role_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'PERMISSIONS_MANAGE','COURSES_WRITE','RECRUITMENT_READ','RECRUITMENT_WRITE'));

alter table member_permissions drop constraint member_permissions_permission_check;
alter table member_permissions add constraint member_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'PERMISSIONS_MANAGE','COURSES_WRITE','RECRUITMENT_READ','RECRUITMENT_WRITE'));
