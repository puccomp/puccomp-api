package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.TimePoint;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Relatório temporal do quadro. Separado de {@code /members/summary} porque descreve outra coisa:
 * lá é o estado atual e o seu recorte; aqui é o que aconteceu numa janela, sobre a EJ inteira.
 *
 * <p>Tudo sai do histórico conhecido. Onde a cobertura não alcança, o valor é nulo — nunca zero:
 * "não houve movimento" e "não sei se houve" não são a mesma resposta.
 */
public record MemberHistoryResponse(
        Period period,

        @Schema(name = "previous_period")
        Period previousPeriod,

        @Schema(description = "Fuso em que os meses são recortados", example = "America/Sao_Paulo")
        String timezone,

        @Schema(description = "Escopo do relatório; sempre TENANT", example = "TENANT")
        String scope,

        Coverage coverage,

        @Schema(name = "active_headcount", description = "Quadro ativo, em pessoas, imediatamente "
                + "antes do encerramento de cada janela")
        Metric activeHeadcount,

        @Schema(description = "Saídas de ACTIVE na janela divididas pelo quadro médio ponderado pelo "
                + "tempo ativo. Fração sem limite superior — pode passar de 1. Seis casas decimais. "
                + "null quando o quadro médio é zero")
        Metric turnover,

        @Schema(name = "average_tenure_months",
                description = "Média, em meses de 365,2425/12 dias, da duração dos intervalos ativos "
                        + "encerrados na janela cujo início é conhecido. Duas casas decimais. "
                        + "null sem intervalos elegíveis")
        Metric averageTenureMonths,

        @Schema(name = "joins_by_month", description = "Primeiras ativações conhecidas de membros "
                + "criados sob rastreamento. Reativação não é nova admissão")
        List<TimePoint> joinsByMonth,

        @Schema(name = "reactivations_by_month", description = "Ativações após um intervalo ativo anterior")
        List<TimePoint> reactivationsByMonth,

        @Schema(name = "headcount_by_month", description = "Quadro ativo imediatamente antes do limite "
                + "exclusivo de cada mês")
        List<TimePoint> headcountByMonth,

        @Schema(description = "Admissões conhecidas por semestre civil e retenção no encerramento da "
                + "janela. null quando a janela não está integralmente coberta")
        List<Cohort> cohorts
) {

    @Schema(description = "Janela meia-aberta: from inclusive, to exclusivo")
    public record Period(
            @Schema(description = "Instante inicial, inclusive") Instant from,
            @Schema(description = "Instante final, exclusivo") Instant to,
            @Schema(description = "Quantidade de meses civis") long months
    ) { }

    /**
     * O que o relatório sabe sobre si mesmo. Publicado junto porque uma métrica nula é ambígua sem
     * ele: pode ser ausência de amostra ou ausência de cobertura, e a decisão do leitor muda.
     */
    public record Coverage(
            @Schema(name = "tracked_since", description = "Início da cobertura do histórico desta EJ")
            Instant trackedSince,

            @Schema(name = "period_complete", description = "A janela está inteiramente dentro da cobertura")
            boolean periodComplete,

            @Schema(name = "previous_period_complete") boolean previousPeriodComplete,

            @Schema(name = "unknown_join_dates",
                    description = "Membros de baseline até o encerramento da janela: estavam na EJ quando "
                            + "o rastreamento começou, e a data de entrada deles é desconhecida. "
                            + "Reativar um deles não recupera essa data. null se a janela não é coberta")
            Long unknownJoinDates,

            @Schema(name = "excluded_tenure_intervals",
                    description = "Intervalos encerrados na janela sem início conhecido, portanto fora "
                            + "de average_tenure_months")
            Metric excludedTenureIntervals
    ) { }

    /**
     * Retenção aqui é presença no encerramento da janela, não permanência ininterrupta: quem saiu e
     * voltou continua contado. E a coincidência de semestre não liga a coorte a um processo seletivo.
     */
    public record Cohort(
            @Schema(description = "Semestre civil da admissão: AAAA.1 é janeiro–junho, "
                    + "AAAA.2 é julho–dezembro", example = "2026.1")
            String period,

            @Schema(description = "Pessoas distintas admitidas no semestre, dentro da janela")
            long joined,

            @Schema(name = "still_active", description = "Quantas delas estavam ativas imediatamente "
                    + "antes do encerramento da janela")
            long stillActive,

            @Schema(description = "still_active dividido por joined, com seis casas decimais")
            BigDecimal retention,

            @Schema(name = "partial_period", description = "A janela corta o semestre, então a coorte "
                    + "também é parcial")
            boolean partialPeriod
    ) { }
}
