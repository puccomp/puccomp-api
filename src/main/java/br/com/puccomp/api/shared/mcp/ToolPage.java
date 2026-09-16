package br.com.puccomp.api.shared.mcp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * O recorte paginado que toda ferramenta MCP de listagem devolve, e o pedido que o origina.
 *
 * <p>O {@code Page} do Spring Data leva junto {@code pageable}, {@code sort}, {@code first} e afins
 * — envelope que o agente relê a cada chamada para decidir uma única coisa: se vale pedir a próxima
 * página. Sendo o mesmo formato em todo módulo, ele aprende a ler um envelope só.
 */
public record ToolPage<T>(List<T> items, long total, int page, int pages) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public static <T> ToolPage<T> of(Page<T> page) {
        return new ToolPage<>(page.getContent(), page.getTotalElements(),
                page.getNumber(), page.getTotalPages());
    }

    /** Lista inteira, sem paginação na origem — catálogo pequeno, por exemplo. */
    public static <T> ToolPage<T> of(List<T> all) {
        return new ToolPage<>(all, all.size(), 0, all.isEmpty() ? 0 : 1);
    }

    /** Página e tamanho chegam como o agente mandou: nulo, negativo e exagerado são normalizados. */
    public static PageRequest request(Integer page, Integer size) {
        return PageRequest.of(page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }

    public static PageRequest request(Integer page, Integer size, Sort sort) {
        return request(page, size).withSort(sort);
    }
}
