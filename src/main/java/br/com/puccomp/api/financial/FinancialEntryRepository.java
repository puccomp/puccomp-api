package br.com.puccomp.api.financial;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.UUID;

interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID>, JpaSpecificationExecutor<FinancialEntry> {

    Page<FinancialEntry> findAll(Specification<FinancialEntry> specification, Pageable pageable);

    static Specification<FinancialEntry> filters(LocalDate from, LocalDate to, FinancialEntryType type) {
        return (root, query, criteria) -> {
            var predicate = criteria.conjunction();
            if (from != null)
                predicate = criteria.and(predicate, criteria.greaterThanOrEqualTo(root.get("date"), from));
            if (to != null)
                predicate = criteria.and(predicate, criteria.lessThanOrEqualTo(root.get("date"), to));
            if (type != null)
                predicate = criteria.and(predicate, criteria.equal(root.get("type"), type));
            return predicate;
        };
    }
}
