package br.com.puccomp.api.organization.members.summary;

import br.com.puccomp.api.organization.members.MemberFilter;
import br.com.puccomp.api.organization.members.MemberStatus;
import br.com.puccomp.api.shared.aggregation.CategoryKey;
import br.com.puccomp.api.organization.members.history.MemberHistoryService;
import br.com.puccomp.api.organization.members.history.ReportWindow;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import br.com.puccomp.api.shared.aggregation.CalendarMonths;
import br.com.puccomp.api.shared.aggregation.Metric;
import br.com.puccomp.api.shared.aggregation.Slice;
import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private final MemberHistoryService history;
    private final Clock clock;

    /** Quais blocos de contexto quem chamou tem permissão de ver. */
    public record ContextAccess(boolean roles, boolean departments) { }

    /** Quantas categorias identificadas cabem numa distribuição por recurso antes da cauda. */
    public record SliceLimit(Integer value) {

        private static final int MAX = 20;

        public SliceLimit {
            if (value != null && (value < 1 || value > MAX))
                throw new ValidationException(
                        "slice_limit vai de 1 a %d; sem ele a distribuição vem inteira".formatted(MAX));
        }

        public static SliceLimit none() {
            return new SliceLimit(null);
        }
    }

    /**
     * Isolamento acima do padrão porque o resumo é várias consultas: em {@code READ COMMITTED} um
     * membro criado no meio entraria no total e não na distribuição, e a resposta se contradiria.
     * Marcar a transação como somente leitura não daria essa garantia — só declara a intenção.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MemberSummaryResponse summarize(MemberFilter filter, ContextAccess access,
                                          SliceLimit limit, int turnoverMonths) {
        ReportWindow window = ReportWindow.lastMonths(turnoverMonths, OrganizationTime.ZONE,
                clock.instant());
        BigDecimal turnover = history.turnoverOf(aggregations.ids(filter), window);
        long total = aggregations.total(filter);
        var active = aggregations.activeCounts(filter);

        return new MemberSummaryResponse(
                Metric.of(total),
                Metric.of(active.active()),
                capped(slices(aggregations.byDepartment(filter), total, "Sem diretoria"), total, limit),
                capped(slices(aggregations.byRole(filter), total, "Sem cargo"), total, limit),
                capped(slices(aggregations.byCourse(filter), total, "Sem curso"), total, limit),
                statusSlices(aggregations.byStatus(filter), total),
                standingSlices(aggregations.byStanding(filter), total),
                tenure(aggregations.tenures(filter)),
                turnover == null ? null : new Metric(turnover, null),
                turnover == null ? null : new MemberSummaryResponse.Period(
                        window.start(), window.end(), window.months()),
                new MemberSummaryResponse.Gaps(active.withoutRole(), active.withoutDepartment(),
                        slices(aggregations.withoutRoleByDepartment(filter), active.withoutRole(),
                                "Sem diretoria")),
                context(access));
    }

    /**
     * O contexto é consultado por escopo de tenant explícito e só quando autorizado: alcançar
     * {@code Role} por dentro não dispensa a permissão do recurso, apenas a esconderia.
     */
    /**
     * Tempo decorrido: quem está ativo conta até agora, quem saiu conta até a saída. Membro sem
     * data de entrada não vira zero — sai da conta e aparece em {@code unknown_start}.
     */
    private MemberSummaryResponse.Tenure tenure(List<MemberAggregations.TenureRow> rows) {
        Instant now = clock.instant();
        List<BigDecimal> months = rows.stream()
                .filter(row -> row.joinedAt() != null)
                .map(row -> CalendarMonths.of(Duration.between(row.joinedAt(),
                        row.leftAt() != null ? row.leftAt() : now).getSeconds()))
                .sorted()
                .toList();
        long unknown = rows.stream().filter(row -> row.joinedAt() == null).count();

        if (months.isEmpty()) return new MemberSummaryResponse.Tenure(null, null, unknown);
        return new MemberSummaryResponse.Tenure(median(months), average(months), unknown);
    }

    /** Amostra par usa a média dos dois centrais, como manda a definição. */
    private static BigDecimal median(List<BigDecimal> sorted) {
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2), CalendarMonths.SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), CalendarMonths.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Corta a cauda das distribuições por recurso. A categoria sem vínculo nunca entra em OTHERS e
     * nunca ocupa uma das vagas do limite: ela é a acionável, e escondê-la na cauda desfaria o
     * motivo de o corte existir. A soma dos count continua igual ao total.
     */
    private static List<Slice> capped(List<Slice> slices, long total, SliceLimit limit) {
        if (limit.value() == null) return slices;

        List<Slice> identified = slices.stream()
                .filter(slice -> slice.key().kind() == CategoryKey.Kind.ITEM).toList();
        List<Slice> absent = slices.stream()
                .filter(slice -> slice.key().kind() != CategoryKey.Kind.ITEM).toList();
        if (identified.size() <= limit.value()) return slices;

        List<Slice> kept = identified.subList(0, limit.value());
        long tail = identified.stream().skip(limit.value()).mapToLong(slice -> slice.count()).sum();

        List<Slice> result = new java.util.ArrayList<>(kept);
        result.add(Slice.of(CategoryKey.others("Outros"), tail, total));
        result.addAll(absent);
        return List.copyOf(result);
    }

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
        };
    }

    private static String label(Standing standing) {
        return switch (standing) {
            case OWNER -> "Dono";
            case MEMBER -> "Membro";
        };
    }
}
