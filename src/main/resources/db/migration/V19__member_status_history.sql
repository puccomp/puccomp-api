-- Registro durável do ciclo de vida do vínculo. members não tem datas de auditoria: não existe
-- created_at de onde tirar uma data de entrada, e uma coluna com default na migration não recupera
-- o passado. Duas colunas (joined_at/left_at) também não serviriam: apagar left_at na reativação
-- faria uma saída já contabilizada sumir do turnover.

-- Marco de cobertura por EJ: a partir daqui a série é confiável. Antes dele, desconhecido — o que
-- é uma informação diferente de "não houve movimento".
create table organization_tracking (
    id uuid not null,
    tenant_id uuid not null,
    tracked_since timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_organization_tracking_tenant unique (tenant_id)
);

create table member_status_history (
    id uuid not null,
    tenant_id uuid not null,
    member_id uuid not null,
    sequence bigint not null,
    kind varchar(255) not null check (kind in ('BASELINE', 'CREATED', 'STATUS_CHANGED')),
    from_status varchar(255) check (from_status in ('ACTIVE','ALUMNUS','INACTIVE','PENDING')),
    to_status varchar(255) not null check (to_status in ('ACTIVE','ALUMNUS','INACTIVE','PENDING')),
    occurred_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_member_status_history_sequence unique (tenant_id, member_id, sequence),
    constraint fk_member_status_history_member foreign key (member_id) references members (id)
);

-- O relatório varre o histórico de uma EJ inteira por vez, sempre em ordem de vínculo.
create index idx_member_status_history_tenant on member_status_history (tenant_id, member_id, sequence);
create index idx_member_status_history_occurred on member_status_history (tenant_id, occurred_at);

-- Bloqueio durante a transição: sem ele, uma mudança de status confirmada entre o baseline e a
-- entrada em vigor dos novos caminhos de escrita ficaria sem evento, e a série nasceria com um
-- buraco silencioso. A migration roda antes de a aplicação atender tráfego; o lock cobre o resto.
lock table members in exclusive mode;

insert into organization_tracking (id, tenant_id, tracked_since)
select gen_random_uuid(), t.id, now() from tenants t;

-- BASELINE é OBSERVAÇÃO, não entrada nem saída: registra o estado em que o membro foi encontrado
-- no marco. A data em que o vínculo realmente começou permanece desconhecida, e é assim que ela
-- deve continuar — preenchê-la com a data da migration inventaria uma admissão que ninguém viu.
-- Alumni e inativos antigos, pelo mesmo motivo, não viram saídas da migration.
insert into member_status_history (id, tenant_id, member_id, sequence, kind, from_status, to_status, occurred_at)
select gen_random_uuid(), m.tenant_id, m.id, 1, 'BASELINE', null, m.status, tracking.tracked_since
from members m
join organization_tracking tracking on tracking.tenant_id = m.tenant_id;
