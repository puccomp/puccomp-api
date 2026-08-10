create table candidacies (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    privacy_consent_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    process_id uuid not null,
    full_name varchar(255) not null,
    email varchar(255) not null,
    phone varchar(50) not null,
    course varchar(255) not null,
    current_term varchar(50) not null,
    linkedin_url varchar(255),
    portfolio_url varchar(255),
    status varchar(50) not null check (status in ('SUBMITTED')),
    primary key (id),
    constraint fk_candidacies_selection_process foreign key (process_id) references selection_processes (id)
);

-- Único: a checagem prévia no service perde a corrida entre requisições simultâneas.
-- lower(email) porque a consulta é case-insensitive.
create unique index uq_candidacies_process_email on candidacies (process_id, lower(email));

create index idx_candidacies_tenant on candidacies (tenant_id);
