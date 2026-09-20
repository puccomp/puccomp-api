-- O vínculo passa a ter dois estados só: ACTIVE e ALUMNUS, os dois significando "tem vínculo".
-- Sair da EJ deixa de ser um estado e vira soft delete, invisível no contrato: quem consome a API
-- vê o membro sumir, e não um status novo para aprender a ignorar em cada contagem.
--
-- INACTIVE e PENDING nunca foram produzidos por rota nenhuma — só por seed e fixture de dev. Em
-- produção os updates abaixo tocam zero linhas; eles existem para o caso de algum ambiente ter
-- rastro deles.
alter table members
    add column deleted_at timestamp(6) with time zone;

-- INACTIVE significava "fora, sem acesso": o sucessor exato é o soft delete. PENDING nunca foi
-- vínculo — quem modela convite não aceito é invitations —, e por isso também sai de vista. Os
-- dois viram ALUMNUS na projeção porque a linha precisa de um estado válido; ninguém a enxerga
-- enquanto deleted_at estiver preenchido.
update members set deleted_at = now() where status in ('INACTIVE', 'PENDING');
update members set status = 'ALUMNUS' where status in ('INACTIVE', 'PENDING');

alter table members drop constraint members_status_check;
alter table members add constraint members_status_check check (status in ('ACTIVE', 'ALUMNUS'));

-- O histórico guarda a projeção, não o motivo. Trocar INACTIVE/PENDING por ALUMNUS não mexe em
-- intervalo nenhum: o que abre e fecha um intervalo ativo é ser ou não ser ACTIVE.
update member_status_history set from_status = 'ALUMNUS' where from_status in ('INACTIVE', 'PENDING');
update member_status_history set to_status = 'ALUMNUS' where to_status in ('INACTIVE', 'PENDING');

alter table member_status_history drop constraint member_status_history_from_status_check;
alter table member_status_history drop constraint member_status_history_to_status_check;
alter table member_status_history
    add constraint member_status_history_from_status_check check (from_status in ('ACTIVE', 'ALUMNUS'));
alter table member_status_history
    add constraint member_status_history_to_status_check check (to_status in ('ACTIVE', 'ALUMNUS'));

-- A deleção é um fato do vínculo como os outros: sem ela no histórico, o intervalo ativo de quem
-- foi removido nunca fecha, e o turnover passaria a contar para sempre um membro que saiu.
alter table member_status_history drop constraint member_status_history_kind_check;
alter table member_status_history add constraint member_status_history_kind_check
    check (kind in ('BASELINE', 'CREATED', 'STATUS_CHANGED', 'DELETED', 'RESTORED'));

-- Toda listagem filtra os vivos; o índice parcial é o que essa consulta realmente usa.
create index idx_members_alive on members (tenant_id) where deleted_at is null;
