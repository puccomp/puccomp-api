package br.com.puccomp.api.organization.members.summary;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composição atual do quadro e o contexto da estrutura da EJ.
 *
 * <p>Os dois blocos descrevem populações diferentes de propósito. A composição descreve o conjunto
 * filtrado; {@code organization_context} descreve a EJ inteira. Um cargo com cinco vagas e cinco
 * ocupantes não passa a ter quatro vagas abertas porque só um ocupante corresponde ao curso filtrado.
 */
public record MemberSummaryResponse(
        @Schema(description = "Membros que atendem ao filtro, em pessoas. previous é sempre nulo: "
                + "este resumo não compara períodos")
        Metric total,

        @Schema(name = "active_headcount",
                description = "Quantos desses membros estão ACTIVE, em pessoas")
        Metric activeHeadcount,

        @Schema(name = "by_department", description = "Da maior para a menor contagem, desempate pelo id "
                + "em ordem textual, com a categoria sem vínculo por último no empate. "
                + "key.id nulo é quem não tem diretoria")
        List<Slice> byDepartment,

        @Schema(name = "by_role", description = "Da maior para a menor contagem, desempate pelo id "
                + "em ordem textual, com a categoria sem vínculo por último no empate. "
                + "key.id nulo é quem não tem cargo")
        List<Slice> byRole,

        @Schema(name = "by_course", description = "Da maior para a menor contagem, desempate pelo id "
                + "em ordem textual")
        List<Slice> byCourse,

        @Schema(name = "by_status", description = "Na ordem declarada em MemberStatus")
        List<Slice> byStatus,

        @Schema(name = "by_standing", description = "Na ordem declarada em Standing")
        List<Slice> byStanding,

        @Schema(description = "Tempo de casa do conjunto filtrado. null quando ninguém do recorte "
                + "tem data de entrada conhecida")
        Tenure tenure,

        @Schema(description = "Saídas de ACTIVE na janela sobre o quadro médio, restrito a quem "
                + "passa pelo filtro. Fração sem limite superior. previous é sempre nulo. "
                + "ATENÇÃO: o recorte usa a atribuição de HOJE — cargo, diretoria e curso não têm "
                + "histórico, então um filtro de diretoria descreve as saídas de quem hoje está "
                + "nela, não as saídas que a diretoria teve. null sem ninguém no recorte")
        Metric turnover,

        @Schema(name = "turnover_period", description = "A janela de meses civis completos usada "
                + "em turnover. null quando turnover é null")
        Period turnoverPeriod,

        Gaps gaps,

        @Schema(name = "organization_context")
        OrganizationContext organizationContext
) {

    /** Janela meia-aberta em que o turnover foi medido: from inclusive, to exclusivo. */
    public record Period(java.time.Instant from, java.time.Instant to, long months) { }

    /**
     * Tempo decorrido na EJ, do primeiro ingresso até agora para quem está ativo, e até a saída
     * para quem saiu. O mesmo campo responde as três leituras: quanto tempo estão aqui, quanto
     * tempo ficaram, ou a mistura.
     *
     * <p><b>Não é o {@code average_tenure_months} do relatório histórico.</b> Lá a média cobre só
     * intervalos ativos encerrados na janela, descontando afastamento; aqui é tempo decorrido da
     * população filtrada, inclusive de quem ainda está. Os dois números são corretos e diferentes,
     * e comparar um com o outro não significa nada.
     */
    public record Tenure(
            @Schema(name = "median_months", description = "Mediana em meses de 365,2425/12 dias, "
                    + "duas casas. É a que se exibe: um fundador de cinco anos puxa a média e faz "
                    + "a equipe parecer mais veterana do que é. null sem ninguém elegível")
            BigDecimal medianMonths,

            @Schema(name = "average_months", description = "Média dos mesmos elegíveis, para "
                    + "comparar com a mediana. null sem ninguém elegível")
            BigDecimal averageMonths,

            @Schema(name = "unknown_start", description = "Membros do recorte sem data de entrada "
                    + "conhecida — de baseline, anteriores ao rastreamento. Ficam fora das duas "
                    + "medidas, e publicá-los evita apresentar a mediana como se cobrisse todos")
            long unknownStart
    ) { }

    /** Contam somente membros ACTIVE do conjunto filtrado — {@code status=ALUMNUS} zera tudo. */
    public record Gaps(
            @Schema(name = "without_role", description = "Membros ativos do recorte sem cargo")
            long withoutRole,

            @Schema(name = "without_department", description = "Membros ativos do recorte sem diretoria")
            long withoutDepartment,

            @Schema(name = "without_role_by_department",
                    description = "Onde estão os ativos sem cargo, da maior contagem para a menor. "
                            + "key.kind ABSENT é quem não tem nem cargo nem diretoria. "
                            + "slice_limit não corta esta lista")
            List<Slice> withoutRoleByDepartment
    ) { }

    /**
     * Contexto da EJ inteira. <b>Nenhum filtro de membros restringe este bloco.</b> Não use seus
     * números como totais da seleção: eles descrevem a estrutura real, não a população filtrada.
     */
    @Schema(description = "Estrutura da EJ inteira. Nenhum filtro de membros o restringe. "
            + "null em um campo significa falta da permissão adicional, não EJ vazia")
    public record OrganizationContext(
            @Schema(description = "Escopo destes números; sempre TENANT", example = "TENANT")
            String scope,

            @Schema(description = "Ocupação de vagas dos cargos ativos. Exige roles:read; "
                    + "sem ela, null")
            Seats seats,

            @Schema(name = "empty_departments",
                    description = "Diretorias ativas sem nenhum membro ativo atribuído, por id crescente. "
                            + "Exige departments:read; sem ela, null")
            List<NamedRef> emptyDepartments,

            @Schema(name = "unfilled_roles",
                    description = "Cargos ativos sem nenhum membro ativo, por id em ordem textual "
                            + "crescente. Inclui cargo "
                            + "com capacidade desconhecida; exclui cargo com capacidade zero. "
                            + "Exige roles:read; sem ela, null")
            List<NamedRef> unfilledRoles
    ) {

        public static OrganizationContext of(Seats seats, List<NamedRef> emptyDepartments,
                                             List<NamedRef> unfilledRoles) {
            return new OrganizationContext("TENANT", seats, emptyDepartments, unfilledRoles);
        }
    }

    /**
     * Os agregados consideram <b>somente cargos ativos com capacidade conhecida</b>, para que
     * {@code open = total - occupied} continue valendo. Quem ocupa cargo ativo de capacidade nula
     * é contado à parte, em {@code occupied_without_capacity}.
     */
    public record Seats(
            @Schema(description = "Soma de max entre os cargos ativos com capacidade conhecida")
            long total,

            @Schema(description = "Ocupantes ativos desses mesmos cargos")
            long occupied,

            @Schema(description = "total - occupied. Saldo líquido: excedente em um cargo compensa "
                    + "vaga em outro numericamente, mas pessoas não são intercambiáveis — "
                    + "para alocar, use by_role")
            long open,

            @Schema(name = "occupied_without_capacity",
                    description = "Ocupantes ativos de cargos ativos cujo max é nulo")
            long occupiedWithoutCapacity,

            @Schema(name = "by_role", description = "Todos os cargos ativos, inclusive sem ocupante, "
                    + "por id em ordem textual crescente")
            List<RoleSeats> byRole
    ) { }

    public record RoleSeats(
            NamedRef role,

            @Schema(description = "Membros ativos atribuídos ao cargo, sem nenhum filtro da requisição")
            long occupied,

            @Schema(description = "Capacidade declarada; null é capacidade desconhecida, "
                    + "que não significa zero nem ilimitada")
            Integer max,

            @Schema(description = "max - occupied; negativo indica excesso de ocupação. "
                    + "null quando max é null")
            Integer open
    ) {

        public static RoleSeats of(NamedRef role, long occupied, Integer max) {
            return new RoleSeats(role, occupied, max,
                    max == null ? null : max - (int) occupied);
        }
    }
}
