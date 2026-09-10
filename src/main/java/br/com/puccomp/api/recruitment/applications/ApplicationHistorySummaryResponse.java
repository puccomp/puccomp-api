package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Retrato do funil da EJ inteira, e não de um processo. Responde o que nenhum processo isolado
 * consegue: quantas pessoas diferentes já passaram pelo funil, quantas voltaram, como o volume
 * evoluiu entre processos e que cursos a EJ nunca alcançou.
 *
 * <p>Não é o resumo por processo sem o {@code process_id}. Fora do prazo de um processo, a curva
 * diária não teria teto e o pico não descreveria nada — "o último dia" do histórico é só o último
 * dia do processo mais recente. No lugar deles entram a série mensal e a distribuição por processo.
 *
 * <p><b>Duas populações.</b> {@code total} e todas as distribuições estão em inscrições e fecham
 * entre si; {@code candidates} está em pessoas e não fecha com nenhuma delas. Quem se inscreveu em
 * três processos é três inscrições e uma pessoa.
 */
public record ApplicationHistorySummaryResponse(
        @Schema(description = "Inscrições que atendem ao filtro, em inscrições — não em pessoas. "
                + "previous é sempre nulo: este resumo não compara períodos")
        Metric total,

        @Schema(description = "O mesmo conjunto contado em pessoas. Não some com as distribuições: "
                + "elas descrevem inscrições")
        Candidates candidates,

        @Schema(name = "with_cv", description = "Quantas inscrições trouxeram currículo")
        Metric withCv,

        @Schema(name = "with_links", description = "Quantas inscrições trouxeram ao menos um link. "
                + "Sobrepõe-se a with_cv; os dois não formam uma distribuição")
        Metric withLinks,

        @Schema(name = "first_submitted_at", description = "Desde quando a EJ tem histórico dentro do filtro")
        Instant firstSubmittedAt,

        @Schema(name = "last_submitted_at") Instant lastSubmittedAt,

        @Schema(name = "processes_covered", description = "Quantos processos diferentes aparecem no "
                + "conjunto filtrado — não quantos a EJ já criou")
        int processesCovered,

        @Schema(name = "distinct_courses", description = "Quantos cursos diferentes apareceram")
        int distinctCourses,

        @Schema(name = "median_term", description = "Período mediano entre quem informou; "
                + "nulo se ninguém no conjunto filtrado informou")
        Short medianTerm,

        @Schema(name = "by_process", description = "Do processo mais recente para o mais antigo, "
                + "desempate por id em ordem textual. É série, não ranking: ordenar por volume "
                + "esconderia justamente a evolução entre processos. key.id é o UUID do processo "
                + "em texto, e serve direto ao filtro process_id da listagem")
        List<Slice> byProcess,

        @Schema(name = "by_course", description = "Da maior para a menor contagem, desempate por id. "
                + "key.id é o UUID do curso em texto")
        List<Slice> byCourse,

        @Schema(name = "by_term", description = "Em ordem crescente de período; key.id nulo é quem "
                + "não informou. key.id é o período em decimal")
        List<Slice> byTerm,

        @Schema(name = "by_month", description = "Volume por mês corrido no fuso America/Sao_Paulo, "
                + "em ordem crescente; a data é o primeiro dia do mês local. Só meses com inscrições "
                + "correspondentes: lacunas não são preenchidas")
        List<TimePoint> byMonth,

        @Schema(name = "untouched_courses", description = "Cursos ativos do catálogo que não "
                + "apareceram no conjunto filtrado, na ordem do catálogo. Sem filtro, é a lista de "
                + "quem a EJ nunca alcançou; com filtro, apenas quem está fora do recorte — a "
                + "distribuição sozinha não os mostraria, porque ela só lista quem apareceu")
        List<NamedRef> untouchedCourses
) {

    /**
     * O funil contado em pessoas. Um candidato é um e-mail, com a mesma normalização que impede a
     * inscrição duplicada dentro de um processo.
     */
    @Schema(description = "O conjunto filtrado contado em pessoas, não em inscrições")
    public record Candidates(
            @Schema(description = "Pessoas diferentes no conjunto filtrado")
            long distinct,

            @Schema(description = "Quantas dessas pessoas têm mais de uma inscrição. Não confunda "
                    + "com total - distinct, que conta inscrições excedentes: três inscrições de "
                    + "uma mesma pessoa são 2 excedentes e 1 reincidente")
            long returning,

            @Schema(name = "returning_share",
                    description = "returning dividido por distinct, de 0 a 1. Nulo quando o filtro "
                            + "não seleciona ninguém")
            Double returningShare
    ) {

        static Candidates of(long distinct, long returning) {
            return new Candidates(distinct, returning,
                    distinct == 0 ? null : (double) returning / distinct);
        }
    }
}
