# .specs

Especificações de trabalho ainda não implementado. Cada arquivo descreve **o que** e **por quê**,
com detalhe suficiente para alguém (ou um agente) implementar sem reabrir a discussão que gerou a
decisão.

Não confundir com [docs/adr/](../docs/adr/README.md): ADR registra decisão de arquitetura já tomada
e vale para sempre. Spec aqui é trabalho pendente e **sai da pasta quando entra na `main`**.

## Ordem

As três primeiras são uma sequência, e a ordem importa: a 001 estabelece a regra na prática, a 002
extrai o vocabulário, e a 003 é quem prova que o vocabulário generaliza. Fazer a 003 antes da 002
significa inventar o formato duas vezes.

| Spec | Assunto | Depende de |
|---|---|---|
| [001](001-summary-respeita-filtros.md) | `/summary` respeita os filtros da listagem irmã | — |
| [002](002-primitivas-de-agregacao.md) | Primitivas de agregação compartilhadas | 001 |
| [003](003-resumo-de-membros.md) | `GET /v1/members/summary` | 002 |

## Convenção que as três estabelecem

**`GET /{coleção}/summary` é sempre irmão de `GET /{coleção}` e aceita os mesmos filtros.**

Resolve "onde ponho essa agregação?" sem discussão, e o front monta a URL sem consultar
documentação. Agregação nunca entra no DTO de detalhe: ele também é devolvido por `POST`, `PUT` e
`PATCH`, e agregar ali faria toda escrita recalcular distribuição à toa.
