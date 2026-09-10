package br.com.puccomp.api.organization.members.summary;

import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

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

        Gaps gaps,

        @Schema(name = "organization_context")
        OrganizationContext organizationContext
) {

    /** Contam somente membros ACTIVE do conjunto filtrado — {@code status=ALUMNUS} zera os dois. */
    public record Gaps(
            @Schema(name = "without_role", description = "Membros ativos do recorte sem cargo")
            long withoutRole,

            @Schema(name = "without_department", description = "Membros ativos do recorte sem diretoria")
            long withoutDepartment
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
