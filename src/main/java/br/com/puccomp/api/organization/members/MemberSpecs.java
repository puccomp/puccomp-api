package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.text.SearchTerm;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Os filtros do quadro em Criteria, compartilhados pela listagem e pelo resumo. Compartilhar só o
 * DTO deixaria os predicados duplicados — e é exatamente aí que a tabela e o gráfico ao lado dela
 * passam a descrever populações diferentes sem ninguém perceber.
 */
public final class MemberSpecs {

    private static final char ESCAPE = '\\';

    private MemberSpecs() { }

    public static Specification<Member> matching(MemberFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.department() != null)
                predicates.add(builder.equal(root.get("department").get("id"), filter.department()));

            if (filter.roleId() != null)
                predicates.add(builder.equal(root.get("role").get("id"), filter.roleId()));

            if (filter.courseId() != null)
                predicates.add(builder.equal(root.get("course").get("id"), filter.courseId()));

            if (filter.status() != null)
                predicates.add(builder.equal(root.get("status"), filter.status()));

            if (filter.standing() != null)
                predicates.add(builder.equal(root.get("standing"), filter.standing()));

            if (filter.hasRole() != null)
                predicates.add(filter.hasRole()
                        ? builder.isNotNull(root.get("role"))
                        : builder.isNull(root.get("role")));

            if (filter.hasDepartment() != null)
                predicates.add(filter.hasDepartment()
                        ? builder.isNotNull(root.get("department"))
                        : builder.isNull(root.get("department")));

            // Contra a coluna gerada, não contra o nome: é ela que está normalizada como o termo e
            // é ela que tem índice. Quem não tem e-mail simplesmente não casa pelo segundo ramo.
            // O default da listagem é quem tem vínculo hoje. Quem saiu não é um status a ignorar
            // em cada contagem: ele simplesmente não está lá.
            if (!Boolean.TRUE.equals(filter.includeDeleted()))
                predicates.add(builder.isNull(root.get("deletedAt")));

            SearchTerm.like(filter.q()).ifPresent(term -> predicates.add(builder.or(
                    builder.like(root.get("searchName"), term, ESCAPE),
                    builder.like(builder.lower(root.get("email")), term, ESCAPE))));

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
