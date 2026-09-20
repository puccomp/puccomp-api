-- joined_at e left_at deixam de ser derivados a cada leitura e viram projeção, mantida pelo
-- MemberLifecycle na mesma transação do evento — o mesmo arranjo que Member.status já usa. O log
-- de eventos continua sendo a fonte de verdade; estas colunas existem porque mediana de
-- permanência e ordenação por entrada precisam do conjunto no banco, não da página.
alter table members
    add column joined_at timestamp(6) with time zone,
    add column left_at timestamp(6) with time zone;

-- Primeira ativação de quem nasceu sob rastreamento. Membro de baseline fica nulo: a entrada dele
-- é anterior ao marco e continua desconhecida, que é diferente de "entrou na data da migration".
update members m
set joined_at = (
    select min(e.occurred_at)
    from member_status_history e
    where e.member_id = m.id
      and e.to_status = 'ACTIVE'
      and e.kind in ('CREATED', 'STATUS_CHANGED')
)
where not exists (
    select 1 from member_status_history b where b.member_id = m.id and b.kind = 'BASELINE'
);

-- Fim do intervalo ativo de quem não está mais ativo. Quem está ACTIVE tem intervalo em aberto e
-- portanto não tem saída; reativar limpa a coluna de novo.
update members m
set left_at = (
    select max(e.occurred_at)
    from member_status_history e
    where e.member_id = m.id
      and e.from_status = 'ACTIVE'
      and ((e.kind = 'STATUS_CHANGED' and e.to_status <> 'ACTIVE') or e.kind = 'DELETED')
)
where m.status <> 'ACTIVE' or m.deleted_at is not null;

-- "Quem entrou mais recentemente" é a ordenação natural da lista de pessoas.
create index idx_members_joined_at on members (tenant_id, joined_at desc);
