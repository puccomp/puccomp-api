-- Inverte a relação entre cargo e diretoria: sai departments.lead_role_id (um cargo líder por
-- diretoria, sem nenhum consumidor no código) e entra roles.department_id (N cargos por diretoria).
-- Sem backfill: o mesmo cargo podia liderar várias diretorias, então a origem seria ambígua.

alter table departments drop constraint uk_departments_tenant_slug;
alter table departments drop column slug;

alter table departments drop constraint fk_departments_lead_role;
alter table departments drop column lead_role_id;

-- Sem on delete cascade: não existe exclusão de diretoria.
alter table roles add column department_id uuid;
alter table roles add constraint fk_roles_department
    foreign key (department_id) references departments (id);

-- Nível hierárquico nunca foi lido: não ordena listagem nem decide autorização.
alter table roles drop column hierarchy_level;
