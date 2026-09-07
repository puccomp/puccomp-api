package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Retrato do funil de um processo. Existe porque, com centenas de inscrições, responder "quantos de
 * cada curso?" pelo endpoint de listagem exigiria o cliente baixar todas as páginas e contar.
 */
public record ApplicationSummaryResponse(
        @Schema(name = "process_id") UUID processId,
        long total,

        @Schema(name = "with_cv", description = "Quantos anexaram currículo")
        long withCv,

        @Schema(name = "with_links", description = "Quantos enviaram ao menos um link")
        long withLinks,

        @Schema(name = "first_submitted_at") Instant firstSubmittedAt,
        @Schema(name = "last_submitted_at") Instant lastSubmittedAt,

        @Schema(name = "by_course", description = "Da maior para a menor contagem")
        List<CourseCount> byCourse,

        @Schema(name = "by_term", description = "Em ordem de período; term nulo é quem não informou")
        List<TermCount> byTerm,

        @Schema(name = "by_day", description = "Curva de chegada, em dias corridos no fuso da EJ")
        List<DayCount> byDay,

        @Schema(name = "peak_day", description = "Dia de maior volume — costuma ser o do prazo")
        DayCount peakDay,

        @Schema(name = "last_day_share",
                description = "Fração do total que chegou no último dia com inscrição, de 0 a 1. "
                        + "Perto de 1 indica que a divulgação só surtiu efeito no fim do prazo.")
        Double lastDayShare,

        @Schema(name = "distinct_courses", description = "Quantos cursos diferentes apareceram")
        int distinctCourses,

        @Schema(name = "median_term", description = "Período mediano entre quem informou; nulo se ninguém informou")
        Short medianTerm
) {
    public record CourseCount(NamedRef course, long count) { }

    public record TermCount(Short term, long count) { }

    public record DayCount(LocalDate date, long count) { }
}
