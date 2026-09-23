package br.com.puccomp.api.organization.members;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduz o nome público do campo de ordenação para a propriedade da entidade, e fecha duas brechas
 * que o {@code Sort} cru deixaria abertas.
 *
 * <p>O resto do contrato é snake_case, e {@code sort=joinedAt} destoaria de {@code course_id} na
 * mesma query string. Campo desconhecido segue intocado: quem decide se ele existe é o Spring Data,
 * com o erro que ele já produz.
 *
 * <p>Data desconhecida vai para o fim nos dois sentidos. No padrão do Postgres o nulo encabeça a
 * ordem decrescente, e a primeira página de quem entrou por último seria só de quem já estava na EJ
 * antes do rastreamento.
 *
 * <p>O id desempata sempre. Sem ele, membros empatados no mesmo status ou na mesma data ausente
 * trocam de lugar entre consultas, e a paginação repete uns e pula outros.
 */
final class MemberSorts {

    private static final Map<String, String> NULLABLE_DATES = Map.of(
            "joined_at", "joinedAt",
            "left_at", "leftAt");

    private MemberSorts() { }

    static Pageable translate(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) return pageable;

        List<Sort.Order> orders = new ArrayList<>(sort.stream().map(MemberSorts::translate).toList());
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id")))
            orders.add(Sort.Order.asc("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private static Sort.Order translate(Sort.Order order) {
        String property = NULLABLE_DATES.get(order.getProperty());
        return property == null ? order : order.withProperty(property).nullsLast();
    }
}
