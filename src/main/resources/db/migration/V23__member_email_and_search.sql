-- O e-mail vira coluna de members em vez de ser resolvido em identity a cada leitura: organization
-- não importa nada de identity (a dependência é de mão única, identity -> organization), e injetar
-- AccountDirectory aqui fecharia um ciclo que ModularityTests.verify() recusa. Além disso o LIKE da
-- busca precisa estar na mesma Specification que já filtra por cargo e diretoria — e-mail resolvido
-- em memória depois da página deixaria busca e paginação descrevendo conjuntos diferentes.
alter table members
    add column email text;

-- Backfill das linhas que nasceram antes desta coluna. Quem não tem conta associada fica nulo:
-- membro de baseline e linha de seed existem sem account_id.
update members m
set email = a.email
from accounts a
where a.id = m.account_id;

-- Coluna gerada, como em candidate_applications (V16): o Postgres garante que ela nunca sai de
-- sincronia com o nome, e o JPQL consegue comparar contra ela — não conseguiria chamar
-- immutable_unaccent direto, e consulta nativa perderia o filtro de tenant do Hibernate.
alter table members
    add column search_name text generated always as (lower(immutable_unaccent(name))) stored;

create index idx_members_search_name on members using gin (search_name gin_trgm_ops);

create index idx_members_search_email on members using gin (lower(email) gin_trgm_ops);
