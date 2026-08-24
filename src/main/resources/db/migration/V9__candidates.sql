-- Extrai a pessoa da inscrição: candidacies guardava a PII em cada linha, então a mesma pessoa
-- em dois processos virava dois registros sem nada ligando um ao outro. Agora candidates é a
-- pessoa, única por EJ e e-mail, e candidacies fica com o vínculo pessoa × processo.
-- course e current_term ficam na inscrição de propósito: são fato do momento em que a pessoa se
-- inscreveu, não atributo dela.

create table candidates (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    full_name varchar(255) not null,
    email varchar(255) not null,
    phone varchar(50) not null,
    linkedin_url varchar(255),
    portfolio_url varchar(255),
    primary key (id)
);

-- Único por EJ, não global: a mesma pessoa pode se candidatar em duas EJs.
-- lower(email) porque a busca é case-insensitive, como em candidacies.
create unique index uq_candidates_tenant_email on candidates (tenant_id, lower(email));

create index idx_candidates_tenant on candidates (tenant_id);

-- Backfill: uma pessoa por (EJ, e-mail), ficando com os dados da inscrição mais recente.
insert into candidates (id, created_at, updated_at, tenant_id, full_name, email, phone, linkedin_url, portfolio_url)
select distinct on (c.tenant_id, lower(c.email))
       gen_random_uuid(), now(), now(), c.tenant_id, c.full_name, c.email, c.phone, c.linkedin_url, c.portfolio_url
from candidacies c
order by c.tenant_id, lower(c.email), c.created_at desc;

alter table candidacies add column candidate_id uuid;

-- O tenant entra no join: sem ele uma inscrição podia apontar para a pessoa de outra EJ.
update candidacies c set candidate_id = ca.id
from candidates ca
where ca.tenant_id = c.tenant_id and lower(ca.email) = lower(c.email);

alter table candidacies alter column candidate_id set not null;

alter table candidacies add constraint fk_candidacies_candidate
    foreign key (candidate_id) references candidates (id);

-- A inscrição passa a ser única por (processo, candidato): o e-mail agora vive em candidates.
drop index if exists uq_candidacies_process_email;
create unique index uq_candidacies_process_candidate on candidacies (process_id, candidate_id);

alter table candidacies drop column full_name;
alter table candidacies drop column email;
alter table candidacies drop column phone;
alter table candidacies drop column linkedin_url;
alter table candidacies drop column portfolio_url;

-- Período vira número: serve para ordenar e comparar faixa, não é texto livre.
alter table candidacies alter column current_term type integer using current_term::integer;
