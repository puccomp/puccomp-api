-- Registra COURSES_WRITE nas check constraints das tabelas de permissão,
-- para que a permissão possa ser concedida a cargos e membros.
-- (OWNER/ADMIN já a recebem por padrão, sem precisar de linha no banco.)

alter table role_permissions drop constraint role_permissions_permission_check;
alter table role_permissions add constraint role_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'PERMISSIONS_MANAGE','COURSES_WRITE'));

alter table member_permissions drop constraint member_permissions_permission_check;
alter table member permissions add constraint member_permissions_permission_check
    check (permission in ('MEMBERS_READ','MEMBERS_WRITE','MEMBERS_INVITE',
        'ROLES_READ','ROLES_WRITE','DEPARTMENTS_READ','DEPARTMENTS_WRITE',
        'sPERMISSIONS_MANAGE','COURSES_WRITE'));