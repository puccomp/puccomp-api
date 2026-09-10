package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Métricas temporais do quadro, calculadas sobre o histórico de vínculos.
 *
 * <p>Duas janelas são avaliadas de forma independente: a anterior sem cobertura implica apenas
 * {@code previous: null}, não invalida a atual. E onde a cobertura não alcança, a resposta é nula —
 * calcular uma janela parcial e apresentá-la como completa seria pior do que não responder.
 */
@Service
@RequiredArgsConstructor
public class MemberHistoryService {

    /** Mês civil médio, para converter duração em meses sem presumir 30 dias. */
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("365.2425")
            .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
    private static final BigDecimal SECONDS_PER_MONTH =
            DAYS_PER_MONTH.multiply(BigDecimal.valueOf(86_400));

    private static final int RATIO_SCALE = 6;
    private static final int MONTHS_SCALE = 2;

    private final MemberStatusEventRepository events;
    private final OrganizationTrackingRepository tracking;

    /**
     * Isolamento acima do padrão: janela atual, janela anterior e coortes precisam enxergar o mesmo
     * histórico, senão um evento gravado no meio da leitura aparece numa métrica e não na outra.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MemberHistoryResponse report(ReportWindow window) {
        Instant trackedSince = tracking.findFirstBy()
                .map(tracking -> tracking.getTrackedSince()).orElse(null);
        ReportWindow previous = window.previous();

        // O histórico inteiro, e não só o recorte: um intervalo que só fecha depois da janela
        // precisa ser conhecido para contribuir com o tempo ativo dentro dela. O que garante que
        // eventos posteriores não mexam num período encerrado são os limites de cada métrica.
        List<MemberHistory> histories = MembershipTimeline.from(events.findAllOrdered());

        boolean covered = covers(trackedSince, window);
        boolean previousCovered = covers(trackedSince, previous);

        return new MemberHistoryResponse(
                period(window),
                period(previous),
                window.zone().getId(),
                "TENANT",
                coverage(trackedSince, window, previous, histories, covered, previousCovered),
                new Metric(covered ? headcountAt(histories, window.end()) : null,
                        previousCovered ? headcountAt(histories, previous.end()) : null),
                new Metric(covered ? turnover(histories, window) : null,
                        previousCovered ? turnover(histories, previous) : null),
                new Metric(covered ? averageTenureMonths(histories, window) : null,
                        previousCovered ? averageTenureMonths(histories, previous) : null),
                series(window, trackedSince, month -> countActivations(histories, window, month, true)),
                series(window, trackedSince, month -> countActivations(histories, window, month, false)),
                series(window, trackedSince,
                        month -> BigDecimal.valueOf(activeCount(histories, window.endOf(month)))),
                covered ? cohorts(histories, window) : null);
    }

    private static MemberHistoryResponse.Period period(ReportWindow window) {
        return new MemberHistoryResponse.Period(window.start(), window.end(), window.months());
    }

    private MemberHistoryResponse.Coverage coverage(Instant trackedSince, ReportWindow window,
                                                    ReportWindow previous, List<MemberHistory> histories,
                                                    boolean covered, boolean previousCovered) {
        return new MemberHistoryResponse.Coverage(
                trackedSince,
                covered,
                previousCovered,
                covered ? countBaselineMembers(histories, window.end()) : null,
                new Metric(
                        covered ? BigDecimal.valueOf(excludedIntervals(histories, window)) : null,
                        previousCovered ? BigDecimal.valueOf(excludedIntervals(histories, previous)) : null));
    }

    /** Cobertura vale para a janela inteira: começar antes do marco é começar no escuro. */
    private static boolean covers(Instant trackedSince, ReportWindow window) {
        return trackedSince != null && !trackedSince.isAfter(window.start());
    }

    private static long countBaselineMembers(List<MemberHistory> histories, Instant limit) {
        return histories.stream()
                .filter(history -> history.fromBaseline())
                .filter(history -> history.baselineAt().isBefore(limit))
                .count();
    }

    private static long excludedIntervals(List<MemberHistory> histories, ReportWindow window) {
        return closedWithin(histories, window).filter(interval -> !interval.startKnown()).count();
    }

    private static BigDecimal headcountAt(List<MemberHistory> histories, Instant instant) {
        return BigDecimal.valueOf(activeCount(histories, instant));
    }

    private static long activeCount(List<MemberHistory> histories, Instant instant) {
        return histories.stream().filter(history -> history.activeJustBefore(instant)).count();
    }

    /**
     * Saídas na janela sobre o quadro médio ponderado pelo tempo: a integral da quantidade ativa ao
     * longo da janela, dividida pela duração dela em segundos. Um denominador simples — quadro no
     * início, ou no fim — trataria uma EJ que dobrou de tamanho como se sempre tivesse tido um tamanho.
     */
    private static BigDecimal turnover(List<MemberHistory> histories, ReportWindow window) {
        long exits = closedWithin(histories, window).count();
        long windowSeconds = Duration.between(window.start(), window.end()).getSeconds();
        long activeSeconds = histories.stream()
                .flatMap(history -> history.intervals().stream())
                .mapToLong(interval -> interval.overlapSeconds(window.start(), window.end()))
                .sum();

        if (activeSeconds == 0) return null;
        BigDecimal averageHeadcount = BigDecimal.valueOf(activeSeconds)
                .divide(BigDecimal.valueOf(windowSeconds), 12, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(exits).divide(averageHeadcount, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Média dos intervalos ENCERRADOS na janela cujo início é conhecido — não tempo acumulado de
     * vida na EJ. Intervalo que já vinha de antes do baseline fica de fora: contá-lo a partir do
     * marco reportaria uma permanência de meses para quem estava na EJ havia anos.
     */
    private static BigDecimal averageTenureMonths(List<MemberHistory> histories, ReportWindow window) {
        List<ActivityInterval> eligible = closedWithin(histories, window)
                .filter(interval -> interval.startKnown()).toList();
        if (eligible.isEmpty()) return null;

        long totalSeconds = eligible.stream()
                .mapToLong(interval -> Duration.between(interval.start(), interval.end()).getSeconds())
                .sum();
        return BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(eligible.size()), 12, RoundingMode.HALF_UP)
                .divide(SECONDS_PER_MONTH, 12, RoundingMode.HALF_UP)
                .setScale(MONTHS_SCALE, RoundingMode.HALF_UP);
    }

    private static java.util.stream.Stream<ActivityInterval> closedWithin(List<MemberHistory> histories,
                                                                          ReportWindow window) {
        return histories.stream()
                .flatMap(history -> history.intervals().stream())
                .filter(interval -> interval.end() != null)
                .filter(interval -> within(interval.end(), window));
    }

    /** Evento exatamente em from entra na janela; exatamente em to pertence à seguinte. */
    private static boolean within(Instant instant, ReportWindow window) {
        return !instant.isBefore(window.start()) && instant.isBefore(window.end());
    }

    private static BigDecimal countActivations(List<MemberHistory> histories, ReportWindow window,
                                               YearMonth month, boolean admissions) {
        Instant start = window.startOf(month);
        Instant end = window.endOf(month);
        long count = histories.stream()
                .flatMap(history -> history.intervals().stream())
                .filter(interval -> interval.startKnown() && interval.admission() == admissions)
                .filter(interval -> !interval.start().isBefore(start) && interval.start().isBefore(end))
                .count();
        return BigDecimal.valueOf(count);
    }

    /** Mês coberto sem eventos vale zero; mês não integralmente coberto vale null, nunca zero presumido. */
    private static List<TimePoint> series(ReportWindow window, Instant trackedSince,
                                          java.util.function.Function<YearMonth, BigDecimal> value) {
        List<TimePoint> points = new ArrayList<>();
        for (YearMonth month : window.monthsInWindow()) {
            boolean monthCovered = trackedSince != null
                    && !trackedSince.isAfter(window.startOf(month));
            points.add(monthCovered
                    ? new TimePoint(window.firstDayOf(month), value.apply(month))
                    : TimePoint.unknown(window.firstDayOf(month)));
        }
        return points;
    }

    /**
     * Coortes pela primeira ativação conhecida, agrupadas por semestre civil. Reativação não troca
     * a coorte, e membro de baseline não entra em nenhuma: ele não tem admissão conhecida.
     */
    private static List<MemberHistoryResponse.Cohort> cohorts(List<MemberHistory> histories,
                                                              ReportWindow window) {
        Map<String, List<MemberHistory>> bySemester = new TreeMap<>();
        Map<String, YearMonth[]> ranges = new LinkedHashMap<>();

        for (MemberHistory history : histories) {
            var admission = history.admission().orElse(null);
            if (admission == null || !within(admission.start(), window)) continue;

            YearMonth month = YearMonth.from(admission.start().atZone(window.zone()));
            int half = month.getMonthValue() <= 6 ? 1 : 2;
            String label = "%d.%d".formatted(month.getYear(), half);
            bySemester.computeIfAbsent(label, key -> new ArrayList<>()).add(history);
            ranges.putIfAbsent(label, new YearMonth[] {
                    YearMonth.of(month.getYear(), half == 1 ? 1 : 7),
                    YearMonth.of(month.getYear(), half == 1 ? 7 : 1).plusYears(half == 1 ? 0 : 1)});
        }

        return bySemester.entrySet().stream()
                .map(entry -> cohort(entry.getKey(), entry.getValue(), ranges.get(entry.getKey()), window))
                .sorted(Comparator.comparing(cohort -> cohort.period()))
                .toList();
    }

    private static MemberHistoryResponse.Cohort cohort(String label, List<MemberHistory> members,
                                                       YearMonth[] range, ReportWindow window) {
        long joined = members.size();
        long stillActive = members.stream()
                .filter(history -> history.activeJustBefore(window.end())).count();
        // Semestre cortado pela janela: a coorte também é parcial, e comparar retenções entre uma
        // coorte inteira e meia coorte não diria nada.
        boolean partial = range[0].isBefore(window.from()) || range[1].isAfter(window.to());

        return new MemberHistoryResponse.Cohort(label, joined, stillActive,
                BigDecimal.valueOf(stillActive)
                        .divide(BigDecimal.valueOf(joined), RATIO_SCALE, RoundingMode.HALF_UP),
                partial);
    }
}
