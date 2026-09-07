# 001 — `/summary` respeita os filtros da listagem irmã

**Estado:** pendente · **Tamanho:** pequeno · **Migration:** não

## Problema

`GET /v1/recruitment/processes/{id}/applications` aceita seis filtros combináveis (`q`,
`course_id`, `min_term`, `max_term`, `has_cv`, `from`, `to`). O `/summary` irmão **ignora todos**.

Na tela isso aparece como incoerência direta: o recrutador filtra a tabela por "Ciência da
Computação", vê 40 dos 125 candidatos, e o gráfico ao lado continua mostrando os 125 distribuídos
entre todos os cursos. Os dois números discordam na mesma tela, e o certo é o da tabela.

## O que fazer

`ApplicationSummaryResponse summarize(...)` passa a receber `CandidateApplicationFilter` e a aplicar
`CandidateApplicationSpecs.matching(processId, filter)` em todas as agregações.

O controller já sabe montar o filtro — é o mesmo `@ParameterObject CandidateApplicationFilter` da
listagem:

```java
@GetMapping("/summary")
public ApplicationSummaryResponse summary(@PathVariable UUID processId,
                                          @ParameterObject CandidateApplicationFilter filter,
                                          HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    return service.summarize(processId, filter);
}
```

## O ponto chato: as agregações hoje não usam Specification

`totalsByProcess`, `countByCourse` e `countByTerm` são `@Query` com `where a.process.id = :processId`
fixo. `countByDay` é nativa.

Duas saídas:

**(a) Migrar as agregações para Criteria**, compondo com a mesma `Specification` da listagem. Mais
trabalho, mas um caminho só para filtrar, e novo filtro passa a valer na listagem e no resumo de
graça.

**(b) Repetir os predicados nas `@Query`.** Mais rápido de escrever e garantidamente vai divergir:
o sétimo filtro vai ser adicionado num lugar e esquecido no outro.

**Vá de (a).** É a decisão que a spec 002 depende para generalizar.

A `countByDay` é o caso difícil, por ser nativa (precisa de `at time zone`, que o JPQL não tem).
Opções, em ordem de preferência:

1. Registrar `immutable_unaccent`… não — aqui o que falta é a conversão de fuso. Registrar a função
   de truncamento via `FunctionContributor` do Hibernate e usar Criteria como as outras.
2. Manter nativa e montar o `where` extra a partir do mesmo `CandidateApplicationFilter`, num único
   ponto compartilhado, com teste que compara o total do `by_day` com o `total` do resumo — se
   divergirem, os filtros saíram de sincronia.

A opção 2 é aceitável desde que exista esse teste de consistência. Sem ele, é a opção (b) disfarçada.

## Aceite

- [ ] `?course_id=X` no `/summary` devolve `total` igual ao `page.total_elements` da listagem com o mesmo filtro
- [ ] `by_course` com `?course_id=X` traz **uma** entrada
- [ ] Soma dos `count` de `by_day` é igual ao `total`, com e sem filtro
- [ ] Soma dos `count` de `by_course` é igual ao `total`
- [ ] Filtro que não casa com ninguém devolve resumo zerado, não 404
- [ ] Teste de mutação: remover o filtro de uma das agregações derruba o teste

O primeiro e o terceiro critérios são os que importam — eles são a definição de "os dois números da
tela concordam".
