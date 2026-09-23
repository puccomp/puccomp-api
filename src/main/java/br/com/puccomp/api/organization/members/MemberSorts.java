package br.com.puccomp.api.organization.members;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Traduz o nome público do campo de ordenação para a propriedade da entidade.
 *
 * <p>O resto do contrato é snake_case, e {@code sort=joinedAt} destoaria de {@code course_id} na
 * mesma query string. Campo desconhecido segue intocado: quem decide se ele existe é o Spring Data,
 * com o erro que ele já produz.
 */
final class MemberSorts {

    private static final Map<String, String> ALIASES = Map.of(
            "joined_at", "joinedAt",
            "left_at", "leftAt");

    private MemberSorts() { }

    static Pageable translate(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isEmpty() || sort.stream().noneMatch(order -> ALIASES.containsKey(order.getProperty())))
            return pageable;

        Sort translated = Sort.by(sort.stream()
                .map(order -> order.withProperty(ALIASES.getOrDefault(order.getProperty(),
                        order.getProperty())))
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
    }
}
