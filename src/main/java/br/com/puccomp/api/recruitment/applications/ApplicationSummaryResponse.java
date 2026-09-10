package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Retrato do funil de um processo. Existe porque, com centenas de inscrições, responder "quantos de
 * cada curso?" pelo endpoint de listagem exigiria o cliente baixar todas as páginas e contar.
 *
 * <p>Descreve o mesmo conjunto que a listagem irmã: todos os filtros valem aqui, e nenhum efeito de
 * {@code page}, {@code size} ou {@code sort}.
 */
public record ApplicationSummaryResponse(
        @Schema(name = "process_id") UUID processId,

        @Schema(description = "Inscrições que atendem ao filtro, em pessoas. "
                + "previous é sempre nulo: este resumo não compara períodos")
        Metric total,

        @Schema(name = "with_cv", description = "Quantos anexaram currículo, em pessoas")
        Metric withCv,

        @Schema(name = "with_links", description = "Quantos enviaram ao menos um link, em pessoas. "
                + "Sobrepõe-se a with_cv; os dois não formam uma distribuição. "
                + "Navegável pelo filtro has_links da listagem")
        Metric withLinks,

        @Schema(name = "first_submitted_at") Instant firstSubmittedAt,
        @Schema(name = "last_submitted_at") Instant lastSubmittedAt,

        @Schema(name = "by_course", description = "Da maior para a menor contagem, desempate por id. "
                + "key.id é o UUID do curso em texto")
        List<Slice> byCourse,

        @Schema(name = "by_term", description = "Em ordem crescente de período; key.id nulo é quem não informou. "
                + "key.id é o período em decimal")
        List<Slice> byTerm,

        @Schema(name = "by_day", description = "Curva de chegada em dias corridos no fuso America/Sao_Paulo, "
                + "em ordem crescente. Só dias com inscrições correspondentes: lacunas não são preenchidas")
        List<TimePoint> byDay,

        @Schema(name = "peak_day", description = "Dia de maior volume — costuma ser o do prazo. "
                + "Nulo quando o filtro não seleciona nenhuma inscrição")
        DayCount peakDay,

        @Schema(name = "last_day_share",
                description = "Fração do total que chegou no último dia com inscrição, de 0 a 1. "
                        + "Perto de 1 indica que a divulgação só surtiu efeito no fim do prazo. "
                        + "Nulo quando não há inscrições no conjunto filtrado.")
        Double lastDayShare,

        @Schema(name = "distinct_courses", description = "Quantos cursos diferentes apareceram")
        int distinctCourses,

        @Schema(name = "median_term", description = "Período mediano entre quem informou; "
                + "nulo se ninguém no conjunto filtrado informou")
        Short medianTerm
) {

    public record DayCount(LocalDate date, long count) { }
}
