# 002 — Primitivas de agregação compartilhadas

**Estado:** pendente · **Tamanho:** médio · **Migration:** não · **Depende de:** 001

## Problema

O que não escala em resumo não é a quantidade de endpoints — é a quantidade de **formatos**. Endpoint
é barato: cada módulo tem o seu, ninguém pisa no do outro, cada um é indexável e cacheável do seu
jeito.

Mas se cada resumo inventar o próprio JSON, o front acumula um componente de gráfico por tela, e a
quinta tela custa o mesmo que a primeira.

O `ApplicationSummaryResponse` já usa três formatos recorrentes sem nomeá-los:

| Campo hoje | Forma |
|---|---|
| `by_course`, `by_term` | distribuição categórica |
| `by_day` | série temporal |
| `total`, `with_cv`, `with_links` | métrica escalar |

Nomear isso agora custa um refactor pequeno. Nomear depois de quatro resumos custa quatro migrações
de tela.

## As primitivas

Em `br.com.puccomp.api.shared.reference`, ao lado do `NamedRef`:

```java
/** Uma fatia de uma distribuição categórica. */
public record Slice(NamedRef key, long count, double share) {
    public static Slice of(NamedRef key, long count, long total) { ... }
}

/** Um ponto de série temporal. O valor é BigDecimal para servir contagem e dinheiro. */
public record TimePoint(LocalDate date, BigDecimal value) { }

/** Número único, com o valor do período anterior quando houver comparação. */
public record Metric(BigDecimal value, BigDecimal previous) { }
```

Três decisões dentro disso:

**`share` vem calculado do servidor.** Não é preguiça do front: evita cada gráfico dividir por um
total que pode nem estar na mesma resposta, e evita dois gráficos da mesma tela arredondarem
diferente e somarem 99,8%.

**`key` é `NamedRef`, sempre.** Distribuição por período é numérica e por status é enum, mas ambas
viram `NamedRef(id, name)` — o `id` é o que o front usa para montar o link de "filtrar por esta
fatia", que é a interação natural de clicar numa barra. Categoria sem id (período, status) usa o
próprio valor como id.

**`TimePoint.value` é `BigDecimal`, não `long`.** Custa nada agora e é o que permite a mesma
primitiva servir "inscrições por dia" e "caixa por mês" sem inventar um segundo formato.

## O que muda no que já existe

`ApplicationSummaryResponse` migra:

```
by_course: [{course, count}]        →  [{key, count, share}]
by_term:   [{term, count}]          →  [{key, count, share}]
by_day:    [{date, count}]          →  [{date, value}]
```

`peak_day`, `last_day_share` e `median_term` **permanecem como estão**. Eles não são primitivas, são
leituras derivadas específicas de recrutamento, e o valor deles é justamente serem específicos. A
primitiva padroniza o transporte, não proíbe insight próprio.

É mudança de contrato. Como a #103 pode nem ter chegado em produção quando isto for feito, vale
conferir antes se há cliente consumindo — se não houver, é troca limpa; se houver, entra junto de
uma versão.

## Ganho concreto no front

Um `<DistributionChart slices={...} />` serve curso, cargo, diretoria, status e categoria financeira.
Um `<TimeSeriesChart points={...} />` serve inscrições por dia e caixa por mês.

Tela nova passa a ser: escolher endpoint, escolher componente. Sem parsing novo.

## Aceite

- [ ] As três primitivas existem em `shared/reference` com teste unitário do cálculo de `share`
- [ ] `share` soma 1.0 (± arredondamento) dentro de cada distribuição
- [ ] `ApplicationSummaryResponse` usa as três, e os testes de resumo passam com as asserções ajustadas
- [ ] Nenhum outro módulo importa `ApplicationSummaryResponse` — as primitivas é que são compartilhadas
- [ ] A collection do bruno reflete o novo formato

## O que NÃO fazer

**Endpoint genérico de analytics** (`/v1/analytics?metric=X&groupBy=Y`). É construir um motor de
consulta sobre HTTP: impossível de indexar, difícil de autorizar por permissão, e o front continua
precisando saber quais combinações existem. A flexibilidade é aparente.

**Compor dashboard no backend** (`GET /v1/dashboard`). Precisaria de porta para dentro de todo
módulo e viraria o arquivo que quebra sempre que qualquer um deles mudar — o oposto do que o
Modulith protege. A home chama três endpoints em paralelo e renderiza cada card conforme chega.

**Agregação em DTO de detalhe ou `?include=stats` na listagem.** O detalhe também é devolvido por
`POST`, `PUT` e `PATCH`; agregar ali faz toda escrita recalcular distribuição à toa.
