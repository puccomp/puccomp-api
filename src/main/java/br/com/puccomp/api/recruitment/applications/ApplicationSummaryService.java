package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.recruitment.processes.ProcessDirectory;
import br.com.puccomp.api.shared.aggregation.CategoryKey;
import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.reference.NamedRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.puccomp.api.recruitment.applications.CandidateApplicationAggregations.CourseCount;
import br.com.puccomp.api.recruitment.applications.CandidateApplicationAggregations.DateCount;
import br.com.puccomp.api.recruitment.applications.CandidateApplicationAggregations.TermCount;

/**
 * Os dois retratos agregados das inscrições: o de um processo e o do histórico da EJ.
 *
 * <p>Ambos rodam acima do isolamento padrão porque são várias consultas: em {@code READ COMMITTED}
 * uma inscrição gravada no meio entraria no total e não na distribuição, e a resposta se
 * contradiria. Chamadas HTTP distintas continuam sem prometer o mesmo instante.
 */
@Service
@RequiredArgsConstructor
class ApplicationSummaryService {

    private final CandidateApplicationAggregations aggregations;
    private final CourseCatalog courses;
    private final ProcessDirectory processes;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    ApplicationSummaryResponse summarize(UUID processId, CandidateApplicationFilter filter, ZoneId zone) {
        if (!processes.exists(processId))
            throw new ResourceNotFoundException("Processo seletivo não encontrado");

        var totals = aggregations.totals(processId, filter);
        long total = totals.total();
        var dayCounts = aggregations.byDay(processId, filter, zone);
        var termCounts = aggregations.byTerm(processId, filter);
        var byCourse = byCourse(aggregations.byCourse(processId, filter), total);

        var peak = dayCounts.stream()
                .max(Comparator.comparingLong((DateCount row) -> row.count()).thenComparing(row -> row.date()))
                .map(row -> new ApplicationSummaryResponse.DayCount(row.date(), row.count()))
                .orElse(null);
        Double lastDayShare = total == 0 || dayCounts.isEmpty() ? null
                : (double) dayCounts.getLast().count() / total;

        return new ApplicationSummaryResponse(
                processId,
                Metric.of(total),
                Metric.of(totals.withCv()),
                Metric.of(totals.withLinks()),
                totals.firstSubmittedAt(),
                totals.lastSubmittedAt(),
                byCourse,
                byTerm(termCounts, total),
                series(dayCounts),
                peak,
                lastDayShare,
                byCourse.size(),
                medianTerm(termCounts));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    ApplicationHistorySummaryResponse summarizeHistory(CandidateApplicationFilter filter, ZoneId zone) {
        var totals = aggregations.totals(null, filter);
        long total = totals.total();
        var candidates = aggregations.candidates(null, filter);
        var courseCounts = aggregations.byCourse(null, filter);
        var termCounts = aggregations.byTerm(null, filter);

        var byProcess = aggregations.byProcess(filter).stream()
                .map(row -> Slice.of(CategoryKey.of(row.processId(), row.title()), row.count(), total))
                .toList();
        var byCourse = byCourse(courseCounts, total);

        return new ApplicationHistorySummaryResponse(
                Metric.of(total),
                ApplicationHistorySummaryResponse.Candidates.of(candidates.distinct(), candidates.returning()),
                Metric.of(totals.withCv()),
                Metric.of(totals.withLinks()),
                totals.firstSubmittedAt(),
                totals.lastSubmittedAt(),
                byProcess.size(),
                byCourse.size(),
                medianTerm(termCounts),
                byProcess,
                byCourse,
                byTerm(termCounts, total),
                series(aggregations.byMonth(null, filter, zone)),
                untouchedCourses(courseCounts));
    }

    private List<Slice> byCourse(List<CourseCount> counts, long total) {
        Map<UUID, String> names = courses.namesOf(counts.stream().map(row -> row.courseId()).toList());
        return counts.stream()
                .map(row -> Slice.of(CategoryKey.of(row.courseId(), names.get(row.courseId())),
                        row.count(), total))
                .toList();
    }

    private static List<Slice> byTerm(List<TermCount> counts, long total) {
        return counts.stream()
                .map(row -> Slice.of(row.term() == null
                                ? CategoryKey.absent("Não informado")
                                : CategoryKey.of(row.term(), row.term() + "º período"),
                        row.count(), total))
                .toList();
    }

    private static List<TimePoint> series(List<DateCount> counts) {
        return counts.stream().map(row -> TimePoint.of(row.date(), row.count())).toList();
    }

    /**
     * O complemento da distribuição: ela só lista curso que apareceu, e "nunca alcançamos esse
     * curso" é justamente o que não aparece. Sai do catálogo ativo, então curso desativado fica de
     * fora dos dois lados.
     */
    private List<NamedRef> untouchedCourses(List<CourseCount> reached) {
        var ids = reached.stream().map(row -> row.courseId()).collect(Collectors.toSet());
        return courses.listActive().stream()
                .filter(course -> !ids.contains(course.id()))
                .map(course -> NamedRef.of(course.id(), course.name()))
                .toList();
    }

    /** Mediana a partir das contagens já agrupadas — quem não informou período fica de fora. */
    private static Short medianTerm(List<TermCount> byTerm) {
        var informed = byTerm.stream().filter(row -> row.term() != null).toList();
        long informedTotal = informed.stream().mapToLong(row -> row.count()).sum();
        if (informedTotal == 0) return null;

        long middle = (informedTotal + 1) / 2;
        long running = 0;
        for (var entry : informed) {
            running += entry.count();
            if (running >= middle) return entry.term();
        }
        return null;
    }
}
