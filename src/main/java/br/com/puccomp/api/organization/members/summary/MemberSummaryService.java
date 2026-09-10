package br.com.puccomp.api.organization.members.summary;

import br.com.puccomp.api.organization.members.MemberFilter;
import br.com.puccomp.api.organization.members.MemberStatus;
import br.com.puccomp.api.shared.aggregation.CategoryKey;
import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Monta o resumo do quadro. A composição sai do conjunto filtrado; o contexto organizacional sai
 * do tenant inteiro — e os dois convivem na mesma resposta justamente para que o front não precise
 * inventar o denominador da capacidade a partir de uma seleção.
 */
@Service
@RequiredArgsConstructor
public class MemberSummaryService {

    private final MemberAggregations aggregations;

    /** Quais blocos de contexto quem chamou tem permissão de ver. */
    public record ContextAccess(boolean roles, boolean departments) { }

    /**
     * Isolamento acima do padrão porque o resumo é várias consultas: em {@code READ COMMITTED} um
     * membro criado no meio entraria no total e não na distribuição, e a resposta se contradiria.
     * Marcar a transação como somente leitura não daria essa garantia — só declara a intenção.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MemberSummaryResponse summarize(MemberFilter filter, ContextAccess access) {
        long total = aggregations.total(filter);
        var active = aggregations.activeCounts(filter);

        return new MemberSummaryResponse(
                Metric.of(total),
                Metric.of(active.active()),
                slices(aggregations.byDepartment(filter), total, "Sem diretoria"),
                slices(aggregations.byRole(filter), total, "Sem cargo"),
                slices(aggregations.byCourse(filter), total, "Sem curso"),
                statusSlices(aggregations.byStatus(filter), total),
                standingSlices(aggregations.byStanding(filter), total),
                new MemberSummaryResponse.Gaps(active.withoutRole(), active.withoutDepartment()),
                context(access));
    }

    /**
     * O contexto é consultado por escopo de tenant explícito e só quando autorizado: alcançar
     * {@code Role} por dentro não dispensa a permissão do recurso, apenas a esconderia.
     */
    private MemberSummaryResponse.OrganizationContext context(ContextAccess access) {
        var occupancy = access.roles() ? aggregations.roleOccupancy() : null;
        return MemberSummaryResponse.OrganizationContext.of(
                occupancy == null ? null : seats(occupancy),
                access.departments() ? refs(aggregations.emptyDepartments()) : null,
                occupancy == null ? null : unfilledRoles(occupancy));
    }

    private static MemberSummaryResponse.Seats seats(List<MemberAggregations.RoleOccupancy> occupancy) {
        long capacity = occupancy.stream().filter(role -> role.maxSeats() != null)
                .mapToLong(role -> role.maxSeats()).sum();
        long occupied = occupancy.stream().filter(role -> role.maxSeats() != null)
                .mapToLong(role -> role.occupied()).sum();
        long withoutCapacity = occupancy.stream().filter(role -> role.maxSeats() == null)
                .mapToLong(role -> role.occupied()).sum();

        return new MemberSummaryResponse.Seats(capacity, occupied, capacity - occupied, withoutCapacity,
                occupancy.stream()
                        .map(role -> MemberSummaryResponse.RoleSeats.of(
                                new NamedRef(role.id(), role.name()), role.occupied(), role.maxSeats()))
                        .toList());
    }

    /** Capacidade explicitamente zero não é vaga a preencher; capacidade desconhecida ainda é. */
    private static List<NamedRef> unfilledRoles(List<MemberAggregations.RoleOccupancy> occupancy) {
        return occupancy.stream()
                .filter(role -> role.occupied() == 0)
                .filter(role -> role.maxSeats() == null || role.maxSeats() > 0)
                .map(role -> new NamedRef(role.id(), role.name()))
                .toList();
    }

    private static List<NamedRef> refs(List<MemberAggregations.RefCount> rows) {
        return rows.stream().map(row -> new NamedRef(row.id(), row.name())).toList();
    }

    private static List<Slice> slices(List<MemberAggregations.RefCount> rows, long total, String absent) {
        return rows.stream()
                .map(row -> Slice.of(row.id() == null
                                ? CategoryKey.absent(absent)
                                : CategoryKey.of(row.id(), row.name()),
                        row.count(), total))
                .toList();
    }

    private static List<Slice> statusSlices(List<MemberAggregations.EnumCount<MemberStatus>> rows, long total) {
        return rows.stream()
                .map(row -> Slice.of(CategoryKey.of(row.value(), label(row.value())), row.count(), total))
                .toList();
    }

    private static List<Slice> standingSlices(List<MemberAggregations.EnumCount<Standing>> rows, long total) {
        return rows.stream()
                .map(row -> Slice.of(CategoryKey.of(row.value(), label(row.value())), row.count(), total))
                .toList();
    }

    private static String label(MemberStatus status) {
        return switch (status) {
            case ACTIVE -> "Ativo";
            case ALUMNUS -> "Alumni";
            case INACTIVE -> "Inativo";
            case PENDING -> "Pendente";
        };
    }

    private static String label(Standing standing) {
        return switch (standing) {
            case OWNER -> "Dono";
            case MEMBER -> "Membro";
        };
    }
}
