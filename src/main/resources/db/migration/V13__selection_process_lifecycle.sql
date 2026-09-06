-- A janela de inscrição volta ao processo (V4 tinha opens_at/closes_at, V11 removeu) — desta vez
-- ela governa de fato quem aceita inscrição, em vez de depender de alguém lembrar de fechar.
alter table selection_processes add column opens_at timestamp(6) with time zone;
alter table selection_processes add column closes_at timestamp(6) with time zone;
alter table selection_processes add column result_at timestamp(6) with time zone;

-- IN_REVIEW é onde o processo passa a maior parte da vida: inscrições encerradas, avaliação
-- rolando. Sem ele, encerrado o prazo só restava mentir (seguir OPEN ou declarar CLOSED).
alter table selection_processes drop constraint selection_processes_status_check;
alter table selection_processes add constraint selection_processes_status_check
    check (status in ('DRAFT', 'OPEN', 'IN_REVIEW', 'CLOSED', 'CANCELLED'));

create index idx_selection_processes_tenant_status on selection_processes (tenant_id, status);
