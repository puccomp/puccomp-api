package br.com.puccomp.api.financial.summary;

import br.com.puccomp.api.financial.FinancialEntryType;
import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import br.com.puccomp.api.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;

/**
 * Monta o resumo do extrato. Sem os dois extremos do período não há janela anterior, e a
 * comparação sai nula — indisponível, que é diferente de zero.
 *
 * <p>Isolamento acima do padrão porque são várias consultas: em {@code READ COMMITTED} um
 * lançamento gravado no meio entraria no total e não na distribuição.
 */
@Service
@RequiredArgsConstructor
public class FinancialSummaryService {

    private final FinancialAggregations aggregations;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinancialSummaryResponse summarize(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw new ValidationException("Período inválido: from deve ser menor ou igual a to");

        var current = aggregations.totals(from, to);
        var previous = previousWindow(from, to)
                .map(window -> aggregations.totals(window.from(), window.to()))
                .orElse(null);

        var incomeByCategory = aggregations.byCategory(from, to, FinancialEntryType.INCOME);
        var expenseByCategory = aggregations.byCategory(from, to, FinancialEntryType.EXPENSE);

        return new FinancialSummaryResponse(
                from,
                to,
                Metric.of(current.income(), previous == null ? null : previous.income()),
                Metric.of(current.expense(), previous == null ? null : previous.expense()),
                Metric.of(balanceOf(current), previous == null ? null : balanceOf(previous)),
                Metric.of(current.entries(), previous == null ? null : previous.entries()),
                distribution(incomeByCategory, current.income()),
                distribution(expenseByCategory, current.expense()),
                series(aggregations.byMonth(from, to, FinancialEntryType.INCOME)),
                series(aggregations.byMonth(from, to, FinancialEntryType.EXPENSE)));
    }

    private record Window(LocalDate from, LocalDate to) { }

    /** Mesma largura, encostada em {@code from}: 30 dias se comparam com os 30 dias anteriores. */
    private static Optional<Window> previousWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null) return Optional.empty();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return Optional.of(new Window(from.minusDays(days), from.minusDays(1)));
    }

    private static BigDecimal balanceOf(FinancialAggregations.Totals totals) {
        return totals.income().subtract(totals.expense());
    }

    private static List<FinancialSummaryResponse.CategoryAmount> distribution(
            List<FinancialAggregations.CategoryAmount> counts, BigDecimal overall) {
        return counts.stream()
                .map(row -> FinancialSummaryResponse.CategoryAmount.of(row.category(), row.total(), overall))
                .toList();
    }

    private static List<TimePoint> series(List<FinancialAggregations.MonthAmount> months) {
        return months.stream().map(row -> TimePoint.of(row.month(), row.total())).toList();
    }
}
