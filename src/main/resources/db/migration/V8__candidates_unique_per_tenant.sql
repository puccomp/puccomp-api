-- Corrige a constraint de unicidade de candidatos:
-- A constraint anterior (uq_candidates_email) era global — um mesmo e-mail não
-- podia existir em duas EJs distintas. A regra correta é única por tenant.
-- Usa lower() para garantir que a checagem seja case-insensitive.

alter table candidates drop constraint if exists uq_candidates_email;

create unique index uq_candidates_tenant_email on candidates (tenant_id, lower(email));
