# Módulo Recrutamento

Cobre o ciclo de entrada de novos membros: a EJ publica um processo seletivo e
recebe inscrições por uma superfície pública.

É o único módulo com **escrita anônima** — o porquê do desenho está na
[ADR 0003](../adr/0003-superficie-publica-de-recrutamento.md). Não depende de
nenhum outro módulo de negócio, só de `shared`.

## Entidades

| Entidade | O que representa |
|---|---|
| `SelectionProcess` | A campanha. Um "PS 2026.1": título, edital, período e status. É o container. |
| `Candidate` | A pessoa. Única por `(tenant_id, lower(email))`. Persiste entre processos. |
| `Candidacy` | O vínculo `Candidate` × `SelectionProcess`. 1 candidato : N inscrições. |

`SelectionProcess` **não** é uma vaga. Um PS de EJ é entrada de turma, não
contratação para um cargo específico — por isso não usamos o vocabulário de ATS
(`Job`, `Posting`).

`Candidate` é criado na primeira inscrição (estratégia *find-or-create* em
`CandidacySubmitter`): se o e-mail já existe para a EJ, reutiliza o registro;
caso contrário, cria um novo. Isso permite ao mesmo candidato se inscrever em
processos futuros sem duplicar dados de contato.

`Candidacy` carrega apenas o que é específico de uma inscrição: curso, período
letivo, status e consentimento LGPD. Os dados de contato (nome, e-mail, telefone,
links) vivem em `Candidate`.

## Estados do processo

```
DRAFT ──▶ OPEN ──▶ CLOSED ──▶ FINISHED
  │        │         │
  └────────┴─────────┴──▶ CANCELLED
```

| Estado | Significa |
|---|---|
| `DRAFT` | Existe só para a EJ. Invisível na superfície pública. |
| `OPEN` | Publicado e recebendo inscrições. **Único estado público.** |
| `CLOSED` | Inscrições encerradas; a EJ está avaliando. |
| `FINISHED` | Resultado divulgado. Estado final. |
| `CANCELLED` | Interrompido. Estado final. |

Transição inválida responde `409`. A regra vive no próprio enum, e o
`SelectionProcess` a aplica — não há setter de status.

Status e prazo são **duas travas independentes**: `OPEN` diz que a EJ publicou;
`opensAt`/`closesAt` dizem a janela. A inscrição precisa das duas — um processo
`OPEN` com prazo vencido recusa com `409`. Datas nulas significam "sem limite".

`CandidacyStatus` tem só `SUBMITTED`: a triagem (em análise / aprovado /
reprovado) é card separado, e o contrato não promete o que ainda não entrega.

## Superfície

**Interna** — exige autenticação e permissão `recruitment:read` / `recruitment:write`:

```
GET    /v1/recruitment/processes
POST   /v1/recruitment/processes
GET    /v1/recruitment/processes/{processId}
PUT    /v1/recruitment/processes/{processId}
PATCH  /v1/recruitment/processes/{processId}/status
GET    /v1/recruitment/processes/{processId}/candidacies
```

**Pública** — anônima, sob `/v1/public/**`, só processos `OPEN`:

```
GET    /v1/public/{ejSlug}/processes
GET    /v1/public/{ejSlug}/processes/{processId}
POST   /v1/public/{ejSlug}/processes/{processId}/candidacies
```

O `ejSlug` é traduzido em tenant pelo `PublicTenantFilter` (em `identity/security`),
antes de a requisição chegar ao controller. Nenhum service do módulo resolve
tenant.

## Organização interna

```
recruitment/
├── processes/     ← SelectionProcess, status, superfície interna e pública
├── candidacies/   ← Candidacy, inscrição pública, listagem para a EJ
└── candidates/    ← Candidate, CRUD de candidatos
```

A dependência aponta em **um sentido só**: `candidacies` conhece `processes`,
nunca o contrário. Uma inscrição pertence a um processo; um processo não precisa
saber que inscrições existem.

Essa travessia passa por `ProcessDirectory` — uma interface com os dois métodos
que `candidacies` precisa (achar processo aberto, conferir existência),
implementada por `SelectionProcessService`. `SelectionProcessRepository` continua
*package-private*.

Repositório é detalhe de implementação do agregado dele. Se um pacote vizinho
precisa de dados, ele pede pela porta — não recebe uma chave do banco. O teste
prático: _"se eu tornar esse repositório package-private de novo, o que quebra?"_
Se a resposta for "o service de outro pacote", falta uma porta ali.

## Pendências conhecidas

- **Anexo de currículo** — depende do módulo de arquivos
  ([ADR 0002](../adr/0002-armazenamento-s3.md)). Até lá a inscrição carrega só
  `linkedinUrl` e `portfolioUrl`. Atenção: o upload aqui é **anônimo**, então
  não cabe em URL pré-assinada emitida para um usuário autenticado.
- **Campos de formulário por EJ** — hoje a ficha é fixa. Ver ADR 0003.
- **Triagem** — mover inscrição entre etapas, com nota e parecer.
