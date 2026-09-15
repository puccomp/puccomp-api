-- Exclusão de lançamento passa a deixar rastro: antes, o extrato de um mês já fechado mudava sem
-- que nada registrasse o quê nem quando.
alter table financial_entries add column deleted_at timestamp(6) with time zone;

-- Índice parcial: toda consulta do módulo filtra por lançamento vivo.
create index ix_financial_entries_tenant_live
    on financial_entries (tenant_id, occurred_on desc)
    where deleted_at is null;
