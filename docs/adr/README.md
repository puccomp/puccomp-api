# Architecture Decision Records

Um **ADR** registra uma decisão técnica significativa: o contexto em que ela foi
tomada, as opções consideradas e as consequências. É um documento **imutável** —
uma vez aceito, não se reescreve; se a decisão muda, cria-se um novo ADR que
_supersede_ o anterior.

Seguimos um formato enxuto inspirado no [MADR](https://adr.github.io/madr/).

## Quando escrever um ADR

Escreva quando a decisão é **cara de reverter** ou **não-óbvia** para quem chega
depois. Exemplos: escolher um padrão de arquitetura, uma estratégia de
autenticação, um modelo de dados central, uma biblioteca difícil de trocar.

Não escreva para detalhes triviais ou reversíveis (nome de variável, formatação,
uma dependência utilitária qualquer). Na dúvida: _"daqui a seis meses, alguém vai
querer saber por que fizemos assim?"_

> Não escrevemos ADRs retroativos para toda decisão antiga. O histórico começa
> aqui e cresce a cada nova decisão relevante.

## Como escrever

1. Copie o [`template.md`](template.md).
2. Numere de forma sequencial e contínua: `0002`, `0003`, ...
3. Nomeie o arquivo `NNNN-titulo-em-kebab-case.md`.
4. Abra na PR que implementa a decisão (ou que a formaliza).
5. Adicione a linha correspondente no índice abaixo.

Um ADR nasce como `proposto` e vira `aceito` ao ser mergeado. Para mudar uma
decisão antiga, crie um novo ADR e marque o antigo como `substituído por NNNN`.

## Índice

| # | Título | Status |
|---|---|---|
| [0001](0001-monolito-modular-com-spring-modulith.md) | Monólito modular com Spring Modulith | aceito |
| [0002](0002-armazenamento-s3.md) | Arquitetura de armazenamento de arquivos (S3) | proposto |
| [0003](0003-superficie-publica-de-recrutamento.md) | Superfície pública de recrutamento | aceito |
