package br.com.puccomp.api.shared.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Comparator;
import java.util.UUID;

/**
 * As peças que toda consulta agregada em Criteria repete. Não é uma camada de agregação: cada
 * módulo continua montando as suas consultas — só não mantém a própria cópia destas quatro.
 */
public final class CriteriaAggregates {

    /**
     * Ordem textual, e não a natural do Java: {@code UUID.compareTo} compara dois longs com sinal,
     * e produz uma sequência que nem o cliente nem o Postgres reproduzem.
     */
    public static final Comparator<UUID> BY_TEXTUAL_ID = Comparator.comparing(id -> id.toString());

    private CriteriaAggregates() { }

    /**
     * Replica os predicados da listagem no root da agregação: um {@code Predicate} pertence à
     * árvore em que foi criado, então reaproveita-se a regra, não o objeto.
     */
    public static <T> Predicate matching(Specification<T> specification, Root<T> root,
                                         CriteriaQuery<?> query, CriteriaBuilder builder) {
        return specification.toPredicate(root, query, builder);
    }

    public static Expression<Long> countIf(CriteriaBuilder builder, Predicate condition) {
        return builder.sum(builder.<Long>selectCase().when(condition, 1L).otherwise(0L));
    }

    /** Soma sobre zero linhas é nula no SQL; contagem conhecida sem ocorrências é zero. */
    public static long zeroIfNull(Long count) {
        return count == null ? 0 : count;
    }
}
