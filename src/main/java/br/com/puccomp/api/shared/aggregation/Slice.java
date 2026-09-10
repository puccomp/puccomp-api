package br.com.puccomp.api.shared.aggregation;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Uma categoria de uma distribuição de contagens.
 *
 * <p>A fração vem calculada do servidor para que toda distribuição use o mesmo denominador — o
 * total da mesma resposta. O cliente escolhe a precisão de exibição; rótulos arredondados podem
 * não somar 100%, e isso é apresentação, não dado.
 */
@Schema(description = "Categoria de uma distribuição, com contagem e fração do total")
public record Slice(
        CategoryKey key,

        @Schema(description = "Quantidade nesta categoria", example = "12")
        long count,

        @Schema(description = "count dividido pelo total da distribuição, de 0 a 1", example = "0.24")
        double share
) {

    public Slice {
        if (count < 0)
            throw new IllegalArgumentException("count não pode ser negativo: " + count);
        if (key == null)
            throw new IllegalArgumentException("key não pode ser nulo; use CategoryKey.absent");
    }

    /** Distribuição vazia tem denominador zero: {@code share} é 0, não uma divisão por zero. */
    public static Slice of(CategoryKey key, long count, long total) {
        if (total < 0)
            throw new IllegalArgumentException("total não pode ser negativo: " + total);
        if (count > total)
            throw new IllegalArgumentException("count %d não pode superar o total %d".formatted(count, total));
        return new Slice(key, count, total == 0 ? 0d : (double) count / total);
    }
}
