package br.com.puccomp.api.recruitment.applications;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da listagem em Criteria, e não em JPQL com {@code :param is null or ...}: são seis
 * recortes opcionais e combináveis, e a query única ficaria ilegível além de tropeçar na inferência
 * de tipo de parâmetro nulo do Postgres. Criteria também é gerenciado pelo Hibernate, então o filtro
 * de tenant continua valendo — o que consulta nativa perderia.
 */
final class CandidateApplicationSpecs {

    private static final char ESCAPE = '\\';

    private CandidateApplicationSpecs() { }

    static Specification<CandidateApplication> matching(UUID processId, CandidateApplicationFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (processId != null)
                predicates.add(builder.equal(root.get("process").get("id"), processId));

            SearchTerm.like(filter.q()).ifPresent(term -> predicates.add(builder.or(
                    builder.like(root.get("searchName"), term, ESCAPE),
                    builder.like(builder.lower(root.get("email")), term, ESCAPE))));

            if (filter.courseId() != null)
                predicates.add(builder.equal(root.get("courseId"), filter.courseId()));

            if (filter.minTerm() != null)
                predicates.add(builder.greaterThanOrEqualTo(root.get("currentTerm"), filter.minTerm()));

            if (filter.maxTerm() != null)
                predicates.add(builder.lessThanOrEqualTo(root.get("currentTerm"), filter.maxTerm()));

            if (filter.hasCv() != null)
                predicates.add(filter.hasCv()
                        ? builder.isNotNull(root.get("cvFileId"))
                        : builder.isNull(root.get("cvFileId")));

            if (filter.from() != null)
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));

            if (filter.to() != null)
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filter.to()));

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
