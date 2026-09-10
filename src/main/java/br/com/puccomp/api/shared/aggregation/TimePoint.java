package br.com.puccomp.api.shared.aggregation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto de uma série temporal. A unidade, a granularidade, o fuso, a janela e o preenchimento
 * de lacunas pertencem ao campo que contém a série, não ao tipo.
 */
@Schema(description = "Ponto de uma série temporal. Em série mensal a data é o primeiro dia do mês local")
public record TimePoint(
        @Schema(description = "Data local do ponto", example = "2026-08-01")
        LocalDate date,

        @Schema(description = "Valor do ponto; nulo quando o período não é integralmente coberto",
                example = "14")
        BigDecimal value
) {

    public static TimePoint of(LocalDate date, long value) {
        return new TimePoint(date, BigDecimal.valueOf(value));
    }

    /** Período sem cobertura: desconhecido não é zero. */
    public static TimePoint unknown(LocalDate date) {
        return new TimePoint(date, null);
    }
}
