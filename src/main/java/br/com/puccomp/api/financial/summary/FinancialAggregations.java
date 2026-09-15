package br.com.puccomp.api.financial.summary;

import br.com.puccomp.api.financial.FinancialEntry;
import br.com.puccomp.api.financial.FinancialEntryFilters;
import br.com.puccomp.api.financial.FinancialEntryType;
import br.com.puccomp.api.shared.criteria.CriteriaAggregates;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * As somas do extrato, reusando os mesmos filtros da listagem — inclusive o recorte que esconde
 * lançamento descartado, para os dois nunca divergirem.
 */
@Component
@RequiredArgsConstructor
class FinancialAggregations {

    private final EntityManager entityManager;

    record Totals(BigDecimal income, BigDecimal expense, long entries) { }

    record CategoryAmount(String category, BigDecimal total) { }

    record MonthAmount(LocalDate month, BigDecimal total) { }

    /** Entradas, saídas e quantidade na mesma consulta: três medidas da mesma população. */
    Totals totals(LocalDate from, LocalDate to) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<FinancialEntry> root = query.from(FinancialEntry.class);

        query.select(builder.tuple(
                        sumIf(builder, root, FinancialEntryType.INCOME),
                        sumIf(builder, root, FinancialEntryType.EXPENSE),
                        builder.count(root)))
                .where(matching(root, query, builder, from, to, null));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        return new Totals(zeroIfNull(row.get(0, BigDecimal.class)),
                zeroIfNull(row.get(1, BigDecimal.class)),
                CriteriaAggregates.zeroIfNull(row.get(2, Long.class)));
    }

    List<CategoryAmount> byCategory(LocalDate from, LocalDate to, FinancialEntryType type) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<FinancialEntry> root = query.from(FinancialEntry.class);
        Path<String> category = root.get("category");

        query.select(builder.tuple(category, builder.sum(root.get("amount"))))
                .where(matching(root, query, builder, from, to, type))
                .groupBy(category);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new CategoryAmount(row.get(0, String.class),
                        zeroIfNull(row.get(1, BigDecimal.class))))
                .sorted(Comparator.comparing((CategoryAmount row) -> row.total()).reversed()
                        .thenComparing(row -> row.category()))
                .toList();
    }

    /** {@code occurred_on} é data local sem hora: aqui o fuso não entra na conta. */
    List<MonthAmount> byMonth(LocalDate from, LocalDate to, FinancialEntryType type) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<FinancialEntry> root = query.from(FinancialEntry.class);
        Expression<LocalDate> month = builder.function("month_of", LocalDate.class, root.get("occurredOn"));

        query.select(builder.tuple(month, builder.sum(root.get("amount"))))
                .where(matching(root, query, builder, from, to, type))
                .groupBy(month);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new MonthAmount(row.get(0, LocalDate.class),
                        zeroIfNull(row.get(1, BigDecimal.class))))
                .sorted(Comparator.comparing(row -> row.month()))
                .toList();
    }

    private static Expression<BigDecimal> sumIf(CriteriaBuilder builder, Root<FinancialEntry> root,
                                                FinancialEntryType type) {
        return builder.sum(builder.<BigDecimal>selectCase()
                .when(builder.equal(root.get("type"), type), root.<BigDecimal>get("amount"))
                .otherwise(BigDecimal.ZERO));
    }

    private static Predicate matching(Root<FinancialEntry> root, CriteriaQuery<?> query,
                                      CriteriaBuilder builder, LocalDate from, LocalDate to,
                                      FinancialEntryType type) {
        return CriteriaAggregates.matching(FinancialEntryFilters.of(from, to, type),
                root, query, builder);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
