package br.com.puccomp.api.financial;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros opcionais da listagem, montados dinamicamente: só entra no SQL o filtro que veio na
 * requisição. A alternativa (um JPQL fixo com {@code :from is null or ...}) não funciona aqui —
 * o Postgres não consegue inferir o tipo de um parâmetro que aparece apenas dentro de um
 * {@code is null}, e a consulta quebra em tempo de execução.
 */
final class FinancialEntryFilters {

    private FinancialEntryFilters() {
    }

    static Specification<FinancialEntry> of(LocalDate from, LocalDate to, FinancialEntryType type) {
        return (root, query, criteria) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null)
                predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredOn"), from));
            if (to != null)
                predicates.add(criteria.lessThanOrEqualTo(root.get("occurredOn"), to));
            if (type != null)
                predicates.add(criteria.equal(root.get("type"), type));
            return criteria.and(predicates.toArray(Predicate[]::new));
        };
    }
}
