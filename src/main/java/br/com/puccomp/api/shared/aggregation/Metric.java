package br.com.puccomp.api.shared.aggregation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Valor atual e a comparação com o período anterior, quando existir.
 *
 * <p>Zero é resultado conhecido sem ocorrências; nulo é medida indefinida ou sem cobertura. A
 * unidade — pessoas, fração, meses ou moeda — é documentada pelo campo que contém a métrica.
 */
@Schema(description = "Valor atual e comparação anterior. value nulo é medida indefinida; "
        + "previous nulo é comparação indisponível, nunca zero.")
public record Metric(
        @Schema(description = "Valor do período atual", example = "42")
        BigDecimal value,

        @Schema(description = "Mesmo valor no período anterior", example = "38")
        BigDecimal previous
) {

    /** Contagem sem comparação histórica — o caso de endpoints de estado atual. */
    public static Metric of(long value) {
        return new Metric(BigDecimal.valueOf(value), null);
    }

    public static Metric of(BigDecimal value, BigDecimal previous) {
        return new Metric(value, previous);
    }

    public static Metric of(Long value, Long previous) {
        return new Metric(value == null ? null : BigDecimal.valueOf(value),
                previous == null ? null : BigDecimal.valueOf(previous));
    }
}
