/**
 * Gestão financeira da EJ: registra, consulta e resume lançamentos, isolados por tenant.
 *
 * <p>Exclusão é descarte, não remoção — um extrato que muda sem deixar rastro não explica de onde
 * veio o saldo. Segue sem antecipar categorias estruturadas ou centros de custo: a categoria é o
 * texto que a EJ digitou, e é por ele que o resumo agrupa.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Financial")
package br.com.puccomp.api.financial;
