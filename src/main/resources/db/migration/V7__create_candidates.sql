create table candidates (
    id uuid not null,
    tenant_id uuid not null,
    full_name varchar(255) not null,
    email varchar(255) not null,
    phone varchar(50) not null,
    linkedin_url varchar(255),
    portfolio_url varchar(255),
    primary key (id),
    constraint uq_candidates_email unique (email)
);

create index idx_candidates_tenant on candidates (tenant_id);

alter table candidacies add column candidate_id uuid;

-- Em caso de existirem dados na tabela local, migra-se os dados para a tabela candidates:
insert into candidates (id, tenant_id, full_name, email, phone, linkedin_url, portfolio_url)
select gen_random_uuid(), tenant_id, full_name, email, phone, linkedin_url, portfolio_url from candidacies
on conflict do nothing;

update candidacies c set candidate_id = (
    select id from candidates where email = c.email limit 1
);

-- Garantir que todas as candidaturas possuam um candidato associado
-- (Para bancos novos ou existentes)
alter table candidacies alter column candidate_id set not null;

alter table candidacies add constraint fk_candidacies_candidate foreign key (candidate_id) references candidates (id);

drop index if exists uq_candidacies_process_email;

alter table candidacies add constraint uq_candidacies_process_candidate unique (process_id, candidate_id);

alter table candidacies drop column full_name;
alter table candidacies drop column email;
alter table candidacies drop column phone;
alter table candidacies drop column linkedin_url;
alter table candidacies drop column portfolio_url;
