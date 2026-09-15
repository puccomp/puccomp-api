package br.com.puccomp.api.shared.mcp;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * O recorte paginado que uma ferramenta MCP devolve.
 *
 * <p>Existe porque o {@code Page} do Spring Data leva junto {@code pageable}, {@code sort},
 * {@code first}, {@code numberOfElements} e afins — envelope que o agente relê e paga em contexto a
 * cada chamada, para decidir uma única coisa: se vale pedir a próxima página.
 *
 * <p>É o mesmo formato em toda ferramenta de listagem, de propósito. O agente aprende a ler um
 * envelope, não um por módulo.
 *
 * <p>Os nomes são de uma palavra só e por isso não mudam entre camelCase e snake_case — mas quem
 * serializa isto é o {@code ObjectMapper} da aplicação, e não o mapper interno do Spring AI, para
 * que o conteúdo lá dentro saia igual ao da API REST. Ver {@code MemberTools}.
 */
public record ToolPage<T>(List<T> items, long total, int page, int pages) {

    public static <T> ToolPage<T> of(Page<T> page) {
        return new ToolPage<>(page.getContent(), page.getTotalElements(),
                page.getNumber(), page.getTotalPages());
    }

    /** Para quando a ferramenta devolve um tipo mais enxuto que o da camada REST. */
    public static <S, T> ToolPage<T> of(Page<S> page, Function<S, T> mapper) {
        return new ToolPage<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getNumber(), page.getTotalPages());
    }

    /** Lista inteira, sem paginação na origem — catálogo pequeno, por exemplo. */
    public static <T> ToolPage<T> of(List<T> all) {
        return new ToolPage<>(all, all.size(), 0, all.isEmpty() ? 0 : 1);
    }
}
