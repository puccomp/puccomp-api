# Módulo Recrutamento

Cobre o ciclo de entrada de novos membros: a EJ publica um processo seletivo e
recebe inscrições por uma superfície pública.

É o único módulo com **escrita anônima** — o porquê do desenho está na
[ADR 0003](../adr/0003-superficie-publica-de-recrutamento.md). De negócio, não
depende de nenhum outro módulo.

## Entidades

| Entidade | O que representa |
|---|---|
| `SelectionProcess` | A campanha criada pela EJ: título, descrição e estado. |
| `CandidateApplication` | O snapshot imutável de uma inscrição enviada para um processo. |

`SelectionProcess` **não** é uma vaga. Um PS de EJ é entrada de turma, não
contratação para um cargo específico — por isso não usamos o vocabulário de ATS
(`Job`, `Posting`).

`CandidateApplication` guarda exatamente o que a pessoa enviou: nome, e-mail,
telefone, curso, período letivo, links e instante do consentimento LGPD. Uma
inscrição posterior com o mesmo e-mail não altera as anteriores.

Não existe cadastro independente de candidato. Se no futuro houver portal do
candidato, banco de talentos ou convite individual, esse novo conceito poderá
ser introduzido sem deixar de preservar o snapshot de cada inscrição.

O período (`current_term`) é texto livre e opcional: cada curso tem uma grade
própria, e quem está irregular ou formando não cabe num número.

Os links formam uma lista ordenada com no máximo cinco URLs. Eles pertencem à
inscrição, não a um perfil compartilhado, porque fazem parte do formulário
transmitido naquele momento.

## Estados do processo

```text
DRAFT ──▶ OPEN ──▶ CLOSED
  │         │
  └─────────┴──▶ CANCELLED
```

| Estado | Significa |
|---|---|
| `DRAFT` | Existe só para a EJ e ainda não recebe inscrições. |
| `OPEN` | Está publicado e recebendo inscrições. |
| `CLOSED` | Encerrou as inscrições. Estado final. |
| `CANCELLED` | Foi interrompido antes do fechamento. Estado final. |

`OPEN` é a única fonte de verdade para publicação e recebimento. Não existem
datas paralelas de abertura e fechamento. Transições inválidas respondem `409`.

Uma inscrição persistida já foi submetida, portanto ela não carrega um status
constante. Estados de triagem só entram quando esse caso de uso existir.

## Superfície

**Interna** — exige autenticação e permissão `recruitment:read` / `recruitment:write`:

```http
GET    /v1/recruitment/processes
POST   /v1/recruitment/processes
GET    /v1/recruitment/processes/{processId}
PUT    /v1/recruitment/processes/{processId}
PATCH  /v1/recruitment/processes/{processId}/status
GET    /v1/recruitment/processes/{processId}/applications
```

**Pública** — anônima, sob `/v1/public/**`, somente para processos `OPEN`:

```http
GET    /v1/public/{orgSlug}/processes
GET    /v1/public/{orgSlug}/processes/{processId}
POST   /v1/public/{orgSlug}/processes/{processId}/applications
```

O `orgSlug` é traduzido em tenant pelo `PublicTenantFilter`, em
`identity/security`, antes de a requisição chegar ao domínio. Nenhum código de
recrutamento resolve o tenant por conta própria.

## Organização interna

```text
recruitment/
├── processes/      ← processo, estados e projeções interna/pública
└── applications/   ← inscrição pública e listagem interna
```

`applications` conhece `processes`, nunca o contrário. A travessia passa por
`ProcessDirectory`, que publica apenas a leitura de processo aberto e a
verificação de existência. Os repositórios permanecem package-private.

## Integridade e isolamento

- Uma inscrição pertence ao mesmo tenant do processo, garantido também por FK
  composta no banco.
- Um e-mail pode se inscrever somente uma vez por processo, ignorando caixa.
- O mesmo e-mail pode participar de processos diferentes.
- O comprovante público contém somente `id` e `submitted_at`, sem ecoar PII.
- O e-mail de confirmação é entregue somente depois do commit.

## Pendências conhecidas

- **Anexo de currículo** — depende do módulo de arquivos
  ([ADR 0002](../adr/0002-armazenamento-s3.md)). O upload aqui será anônimo.
- **Campos de formulário por EJ** — hoje a ficha é fixa. Ver ADR 0003.
- **Triagem** — etapas, nota e parecer ainda não fazem parte do contrato.
- **LGPD** — definir política de retenção, anonimização e versão do aviso aceito.
