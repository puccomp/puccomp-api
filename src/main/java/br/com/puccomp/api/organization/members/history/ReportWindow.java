package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.shared.exception.ValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Janela de meses civis completos, no fuso da EJ.
 *
 * <p>É meia-aberta: o início de {@code from} entra e o início de {@code to} não, para que um
 * evento não seja contado em dois meses. O padrão é o último mês civil completo — o mês corrente
 * ainda está acontecendo, e exibi-lo ao lado de meses fechados convidaria a comparar coisas
 * diferentes.
 */
public record ReportWindow(YearMonth from, YearMonth to, ZoneId zone) {

    private static final int MAX_MONTHS = 24;

    public static ReportWindow of(String from, String to, ZoneId zone, Instant now) {
        YearMonth currentMonth = YearMonth.from(now.atZone(zone));
        if (from == null && to == null)
            return new ReportWindow(currentMonth.minusMonths(1), currentMonth, zone);
        if (from == null || to == null)
            throw new ValidationException("Informe from e to juntos, ou nenhum dos dois");

        ReportWindow window = new ReportWindow(parse(from, "from"), parse(to, "to"), zone);
        window.validate(currentMonth);
        return window;
    }

    private void validate(YearMonth currentMonth) {
        long months = months();
        if (months <= 0)
            throw new ValidationException("from deve ser anterior a to");
        if (months > MAX_MONTHS)
            throw new ValidationException("A janela aceita no máximo %d meses completos".formatted(MAX_MONTHS));
        // to pode ser o primeiro mês ainda não concluído, porque o seu início é o limite exclusivo:
        // o que a janela não pode conter é o mês corrente pela metade, nem mês nenhum do futuro.
        if (to.isAfter(currentMonth))
            throw new ValidationException("A janela só cobre meses civis já concluídos");
    }

    private static YearMonth parse(String value, String field) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("%s: use o formato AAAA-MM".formatted(field));
        }
    }

    long months() {
        return java.time.temporal.ChronoUnit.MONTHS.between(from, to);
    }

    Instant start() {
        return startOf(from);
    }

    Instant end() {
        return startOf(to);
    }

    /** Mesma quantidade de meses civis, terminando onde esta começa. Não são "30 dias antes":
     *  meses têm durações diferentes, e o quadro médio depende da duração real. */
    ReportWindow previous() {
        return new ReportWindow(from.minusMonths(months()), from, zone);
    }

    /** Os meses da janela, do primeiro ao último, cada um representado pelo seu primeiro dia local. */
    List<YearMonth> monthsInWindow() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = from; month.isBefore(to); month = month.plusMonths(1))
            months.add(month);
        return months;
    }

    LocalDate firstDayOf(YearMonth month) {
        return month.atDay(1);
    }

    Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    /** Limite exclusivo do mês: o instante em que o mês seguinte começa. */
    Instant endOf(YearMonth month) {
        return startOf(month.plusMonths(1));
    }
}
