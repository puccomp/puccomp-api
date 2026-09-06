create extension if not exists unaccent;
create extension if not exists pg_trgm;

-- unaccent() é STABLE, então não pode entrar em índice nem em coluna gerada. A forma de duas
-- versões, com o dicionário fixado, é determinística — daí poder ser declarada IMMUTABLE.
create or replace function immutable_unaccent(text) returns text
    language sql immutable strict parallel safe as
$$ select public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Coluna gerada em vez de mantida pela aplicação: o Postgres garante que ela nunca sai de sincronia,
-- e o JPQL consegue comparar contra ela (não conseguiria chamar immutable_unaccent direto), o que
-- mantém o filtro de tenant do Hibernate valendo — consulta nativa o ignoraria.
alter table candidate_applications
    add column search_name text generated always as (lower(immutable_unaccent(full_name))) stored;

create index idx_candidate_applications_search_name
    on candidate_applications using gin (search_name gin_trgm_ops);

create index idx_candidate_applications_search_email
    on candidate_applications using gin (lower(email) gin_trgm_ops);
