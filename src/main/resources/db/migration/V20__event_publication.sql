-- Outbox transacional do Spring Modulith: a publicação é gravada na mesma transação do fato, e o
-- aviso que não completou continua pendente em vez de sumir num log.error. Ver ADR 0005.

-- Esquema fixado pelo Modulith 2.x (v2 do registro JDBC), copiado em vez de gerado: o dono do
-- esquema aqui é o Flyway.
create table if not exists event_publication (
    id uuid not null,
    listener_id text not null,
    event_type text not null,
    serialized_event text not null,
    publication_date timestamp with time zone not null,
    completion_date timestamp with time zone,
    status text,
    completion_attempts int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index if not exists event_publication_serialized_event_hash_idx
    on event_publication using hash(serialized_event);

create index if not exists event_publication_by_completion_date_idx
    on event_publication (completion_date);
