# 003 — `GET /v1/members/summary`

**Estado:** pendente · **Tamanho:** grande · **Migration:** sim (fase 2) · **Depende de:** 002

## Por que este é o caso que importa

Não é só "a próxima tela". É o **teste de generalização das primitivas**: se o resumo de membros
couber em `Slice`, `TimePoint` e `Metric` sem inventar campo novo, o desenho da 002 está certo e todo
resumo seguinte vira repetição. Se não couber, é melhor descobrir agora, com dois casos, do que com
cinco.

Por isso a ordem 001 → 002 → 003 não é negociável.

## Convenção

`GET /v1/members/summary`, irmão de `GET /v1/members`, mesma permissão (`members:read`) e **mesmos
filtros**.

Hoje a listagem só filtra por `departmentId`. Como a regra é que os dois andem juntos, esta spec
inclui **expandir os filtros dos dois** para `department_id`, `role_id`, `course_id`, `status` e
`standing` — via `Specification`, exatamente como foi feito em recrutamento.

Cabeçalho: `Cache-Control: private, max-age=60`. Diferente do resumo de recrutamento, aqui não há PII
de candidato — é contagem de gente que já é da EJ. Uma home aberta por várias pessoas ao mesmo tempo
agradece.

---

## Fase 1 — o que dá para fazer com o modelo de hoje

### Composição

Distribuições diretas, todas como `List<Slice>`:

- `by_department` — quantos por diretoria
- `by_role` — quantos por cargo
- `by_course` — de onde vem o time
- `by_status` — `ACTIVE`, `ALUMNUS`, `INACTIVE`, `PENDING`
- `by_standing` — `OWNER` e `MEMBER`

`Slice.key` como `NamedRef` é o que permite clicar numa fatia e cair na listagem já filtrada. Para
`status` e `standing`, que não têm id próprio, o `id` é o próprio valor do enum.

### Ocupação de vagas — o recorte que ninguém está usando

`Role.maxSeats` é capturado na criação do cargo, editável, e devolvido no `RoleResponse` — mas
**nada nunca o compara com a ocupação real**. É um número que a EJ preenche e que o sistema só
devolve de volta, sem transformar em resposta. O dado já está lá desde a V1.

```json
"seats": {
  "total": 24, "occupied": 17, "open": 7,
  "by_role": [
    { "role": {...}, "occupied": 1, "max": 1,  "open": 0 },
    { "role": {...}, "occupied": 3, "max": 8,  "open": 5 },
    { "role": {...}, "occupied": 4, "max": 2,  "open": -2 }
  ]
}
```

O terceiro caso é de propósito: **cargo acima da capacidade**. `open` negativo é alarme de dado
inconsistente — ou o `maxSeats` está desatualizado, ou entrou gente demais. Vale expor em vez de
esconder com `Math.max(0, ...)`.

Cargo com `maxSeats` nulo entra em `by_role` com `max: null` e não soma no `total`.

### Lacunas de cobertura — o que transforma gráfico em tarefa

Esta é a parte que faz o resumo valer mais que um gráfico bonito. São perguntas de saúde
organizacional, todas resolvidas com o que já existe:

```json
"gaps": {
  "without_role": 3,
  "without_department": 2,
  "pending_invites": 5,
  "oldest_pending_invite_days": 47,
  "empty_departments": [ { "id": "...", "name": "Marketing" } ],
  "unfilled_roles":   [ { "id": "...", "name": "Diretor de Projetos" } ]
}
```

- `without_role` / `without_department`: membro ativo sem cargo ou sem diretoria
- `pending_invites`: `status = PENDING`, ou `account_id is null` — convite que ninguém aceitou
- `oldest_pending_invite_days`: convite parado há 47 dias é convite morto, não pendente
- `empty_departments`: diretoria ativa com zero membro ativo
- `unfilled_roles`: cargo ativo com zero ocupante

Um ERP que responde "o que está errado agora" é mais útil que um que responde "quantos somos".

### Entradas por mês

`List<TimePoint>` a partir de `created_at`, agrupado por mês **no fuso do tenant**.

Cuidado registrado: isto é **entrada**, não saldo. Chamar de "evolução do quadro" seria mentira
enquanto a fase 2 não existir, porque saída não é registrada em lugar nenhum.

---

## Fase 2 — o que exige migration, e por quê

### O buraco

Não há como saber **quando** alguém saiu. `updated_at` não serve: qualquer edição de nome ou troca
de cargo o empurra para hoje. Então hoje é impossível responder:

- Qual o turnover do semestre?
- Quanto tempo em média as pessoas ficam?
- Da turma que entrou em 2025.1, quantos continuam?

Para uma EJ, onde rotatividade é a característica estrutural do negócio — todo mundo se forma e sai —
essas são *as* perguntas de gestão de pessoas. É a diferença entre "somos 17" e "somos 17, perdemos
6 no semestre passado e a média de permanência caiu de 14 para 9 meses".

### Migration V18

```sql
alter table members add column joined_at timestamp(6) with time zone;
alter table members add column left_at   timestamp(6) with time zone;

-- Quem já existe herda a criação do vínculo como entrada.
update members set joined_at = created_at where joined_at is null;
alter table members alter column joined_at set not null;

-- Saída não é reconstituível: updated_at não distingue "aposentou" de "trocou de cargo".
-- Alumni e inativos atuais ficam com left_at nulo e entram como "saída de data desconhecida"
-- nas métricas, em vez de inventar uma data plausível.
```

`left_at` é preenchido em `retire()` e em `changeStatus(INACTIVE)`, e limpo em `reactivate()`.

**Limitação aceita conscientemente:** duas colunas guardam o vínculo atual, não o histórico. Quem sai
e volta perde o intervalo anterior. Uma `member_status_history` resolveria de verdade, mas é uma
tabela e um conjunto de regras a mais para um caso que numa EJ é raro. Se virar comum, a migração
para histórico é aditiva — as duas colunas continuam sendo a projeção do último vínculo.

### O que a fase 2 destrava

**Quadro ao longo do tempo, de verdade** — `TimePoint` com o saldo por mês (entradas menos saídas
acumuladas), e não só entradas.

**Turnover** — `Metric` com o valor do período e o do anterior, que é exatamente para isso que o
`Metric.previous` existe:

```json
"turnover": { "value": 0.27, "previous": 0.18 }
```

Saídas no período dividido pelo quadro médio. O `previous` é o que transforma o número em leitura:
0,27 sozinho não diz nada; 0,27 vindo de 0,18 diz que dobrou de patamar.

**Permanência média** — `Metric` em meses, sobre quem já saiu, com o `previous` do período anterior.

**Retenção por coorte** — a mais ambiciosa e a mais útil:

```json
"cohorts": [
  { "cohort": "2025.1", "joined": 12, "still_active": 4, "retention": 0.33 },
  { "cohort": "2025.2", "joined":  9, "still_active": 6, "retention": 0.67 },
  { "cohort": "2026.1", "joined":  7, "still_active": 7, "retention": 1.00 }
]
```

A coorte sai do `joined_at`: ano mais semestre (`mês <= 6 ? 1 : 2`), que é como EJ pensa o tempo —
"a turma do 2025.1". Responde de um jeito que ninguém discute se o processo seletivo passado trouxe
gente que ficou, e é o número que fecha o ciclo com o módulo de recrutamento: adiantou trazer 125
candidatos se a turma evapora em um semestre?

---

## Forma da resposta

```json
{
  "headcount":   { "value": 17, "previous": 21 },
  "by_department": [ { "key": {...}, "count": 6, "share": 0.35 } ],
  "by_role":       [ ... ],
  "by_course":     [ ... ],
  "by_status":     [ ... ],
  "by_standing":   [ ... ],
  "seats": { "total": 24, "occupied": 17, "open": 7, "by_role": [ ... ] },
  "gaps":  { "without_role": 3, "...": "..." },
  "joins_by_month":     [ { "date": "2026-03-01", "value": 4 } ],
  "headcount_by_month": [ { "date": "2026-03-01", "value": 17 } ],
  "turnover":           { "value": 0.27, "previous": 0.18 },
  "average_tenure_months": { "value": 9.4, "previous": 14.1 },
  "cohorts": [ ... ]
}
```

Os quatro últimos só existem depois da fase 2. Na fase 1 vão como `null` — e não omitidos, para o
front poder esconder o card sem tratar campo ausente como erro.

## Fronteira de módulo

Tudo aqui é de `organization`: `Member`, `Role`, `Department`, `Course` são todos dele.

**Convite não entra.** `Invitation` é de `identity`, e puxar isso para cá abriria uma dependência
nova só para um card. O que dá para saber sem cruzar módulo — `status = PENDING` e `account_id is
null` — é o que a EJ precisa (`pending_invites`). Um funil de convite completo, se um dia fizer
sentido, é `GET /v1/invitations/summary` no módulo dele, e a home chama os dois.

## Aceite

**Fase 1**
- [ ] `/v1/members/summary` responde com `members:read`, e 403 sem ela
- [ ] Filtros da listagem e do resumo são o mesmo objeto, via `Specification`
- [ ] `headcount.value` bate com `page.total_elements` da listagem sob o mesmo filtro
- [ ] Cada distribuição soma `headcount.value`, e `share` soma 1.0
- [ ] `seats.by_role` mostra `open` negativo quando há mais ocupantes que `max_seats`
- [ ] Cargo com `max_seats` nulo não entra em `seats.total`
- [ ] `gaps.empty_departments` só traz diretoria ativa
- [ ] Resumo de EJ vazia devolve zeros e listas vazias, nunca `null` nas listas
- [ ] Isolamento entre EJs: resumo de uma não enxerga membro da outra
- [ ] Mutação: ignorar o filtro em qualquer agregação derruba um teste

**Fase 2**
- [ ] V18 aplica com `joined_at` herdando `created_at`
- [ ] `retire()` grava `left_at`; `reactivate()` limpa
- [ ] Alumni anterior à migration entra nas métricas como saída de data desconhecida, sem data inventada
- [ ] `turnover.previous` compara com o período imediatamente anterior de mesma duração
- [ ] Coorte deriva de `joined_at` no formato `AAAA.S`

## Riscos

**Muitas consultas.** São cinco distribuições, mais `seats`, mais seis lacunas, mais séries — se cada
uma virar um `SELECT`, o endpoint faz quinze idas ao banco. Com 17 membros isso é irrelevante e não
vale otimizar agora; o que vale é **medir** antes de crescer. Se doer, o caminho é uma consulta com
`grouping sets` ou `filter (where ...)`, que resolve várias contagens numa passada.

**Fuso de novo.** `joins_by_month` tem o mesmo problema que o `by_day` de recrutamento: agrupar em
UTC joga o fim do mês para o mês seguinte. O fuso segue constante `America/Sao_Paulo` até virar
configuração de tenant — e este é o segundo lugar que pede isso, o que reforça que já está na hora.

**`headcount.previous` precisa de período.** Comparar com "o anterior" exige saber qual é a janela.
Sem `from`/`to` no filtro, o padrão é mês corrente contra mês anterior — e isso tem que estar
documentado na descrição do endpoint, senão vira número mágico.
