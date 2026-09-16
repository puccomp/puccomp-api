package br.com.puccomp.api.financial.summary;

import br.com.puccomp.api.shared.aggregation.CategoryKey;
import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Retrato do caixa da EJ no período. Existe porque chegar a estes números pela listagem exigiria
 * baixar todas as páginas e somar no cliente, com o risco de dois clientes somarem diferente.
 *
 * <p>Descreve o mesmo conjunto que a listagem irmã: mesmos filtros, e descartado fora dos dois.
 */
@Schema(description = "Somas, distribuições e séries do extrato no período")
public record FinancialSummaryResponse(

        @Schema(description = "Primeiro dia coberto; nulo quando não houve recorte inicial")
        LocalDate from,

        @Schema(description = "Último dia coberto; nulo quando não houve recorte final")
        LocalDate to,

        @Schema(description = "Total de entradas no período, em reais")
        Metric income,

        @Schema(description = "Total de saídas no período, em reais")
        Metric expense,

        @Schema(description = "Entradas menos saídas. Negativo é déficit no período — "
                + "não é o saldo acumulado da EJ, que dependeria de um saldo inicial")
        Metric balance,

        @Schema(description = "Quantidade de lançamentos que atendem ao filtro")
        Metric entries,

        @Schema(description = "Entradas por categoria, da maior para a menor")
        List<CategoryAmount> incomeByCategory,

        @Schema(description = "Saídas por categoria, da maior para a menor")
        List<CategoryAmount> expenseByCategory,

        @Schema(description = "Entradas por mês de competência, em ordem cronológica")
        List<TimePoint> incomeByMonth,

        @Schema(description = "Saídas por mês de competência, em ordem cronológica")
        List<TimePoint> expenseByMonth
) {

    /**
     * Categoria de uma distribuição em dinheiro. Não é {@code Slice}, que conta linhas; a chave é a
     * mesma {@link CategoryKey} das outras distribuições.
     */
    @Schema(description = "Categoria de uma distribuição em dinheiro, com valor e fração do total")
    public record CategoryAmount(

            CategoryKey key,

            @Schema(description = "Soma da categoria, em reais", example = "1250.00")
            BigDecimal total,

            @Schema(description = "total dividido pela soma da distribuição, de 0 a 1", example = "0.31")
            double share
    ) {

        static CategoryAmount of(String category, BigDecimal total, BigDecimal overall) {
            double share = overall == null || overall.signum() == 0
                    ? 0d
                    : total.divide(overall, 6, java.math.RoundingMode.HALF_UP).doubleValue();
            return new CategoryAmount(CategoryKey.of(category), total, share);
        }
    }
}
