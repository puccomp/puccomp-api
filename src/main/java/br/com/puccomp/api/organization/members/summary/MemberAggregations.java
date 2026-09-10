package br.com.puccomp.api.organization.members.summary;

import br.com.puccomp.api.organization.members.Member;
import br.com.puccomp.api.organization.members.MemberFilter;
import br.com.puccomp.api.organization.members.MemberSpecs;
import br.com.puccomp.api.organization.members.MemberStatus;
import br.com.puccomp.api.organization.departments.Department;
import br.com.puccomp.api.organization.roles.Role;
import br.com.puccomp.api.shared.reference.Standing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consultas do resumo de membros, separadas em duas famílias que não se misturam.
 *
 * <p>As da composição recebem o {@code MemberFilter} e reusam a mesma {@code Specification} da
 * listagem. As do contexto organizacional não recebem filtro nenhum: descrevem a estrutura da EJ,
 * e deixá-las herdar o recorte inventaria vagas abertas e diretorias vazias que não existem.
 */
@Component
@RequiredArgsConstructor
class MemberAggregations {

    /**
     * Ordem textual do UUID, não a natural do Java: {@code UUID.compareTo} compara dois longs com
     * sinal, então a sequência que ela produz não é a que o cliente vê nos ids da resposta — nem a
     * que o Postgres usa. Aqui a ordem publicada é a que quem consome consegue reproduzir.
     */
    private static final Comparator<UUID> BY_ID = Comparator.comparing(id -> id.toString());

    private final EntityManager entityManager;

    /** Categoria de uma distribuição por recurso: id e nome saem da mesma consulta. */
    record RefCount(UUID id, String name, long count) { }

    record EnumCount<E extends Enum<E>>(E value, long count) { }

    record RoleOccupancy(UUID id, String name, Integer maxSeats, long occupied) { }

    long total(MemberFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Member> root = query.from(Member.class);
        query.select(builder.count(root)).where(matching(root, query, builder, filter));
        return entityManager.createQuery(query).getSingleResult();
    }

    /**
     * Ativos do recorte, com as lacunas de vínculo na mesma consulta: são três contagens sobre a
     * mesma população, e separá-las só multiplicaria idas ao banco.
     */
    ActiveCounts activeCounts(MemberFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Member> root = query.from(Member.class);
        Predicate active = builder.equal(root.get("status"), MemberStatus.ACTIVE);

        query.select(builder.tuple(
                        countIf(builder, active),
                        countIf(builder, builder.and(active, builder.isNull(root.get("role")))),
                        countIf(builder, builder.and(active, builder.isNull(root.get("department"))))))
                .where(matching(root, query, builder, filter));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        return new ActiveCounts(value(row.get(0, Long.class)), value(row.get(1, Long.class)),
                value(row.get(2, Long.class)));
    }

    record ActiveCounts(long active, long withoutRole, long withoutDepartment) { }

    List<RefCount> byDepartment(MemberFilter filter) {
        return byReference(filter, "department");
    }

    List<RefCount> byRole(MemberFilter filter) {
        return byReference(filter, "role");
    }

    List<RefCount> byCourse(MemberFilter filter) {
        return byReference(filter, "course");
    }

    List<EnumCount<MemberStatus>> byStatus(MemberFilter filter) {
        return byEnum(filter, "status", MemberStatus.class);
    }

    List<EnumCount<Standing>> byStanding(MemberFilter filter) {
        return byEnum(filter, "standing", Standing.class);
    }

    /**
     * Left join para não perder quem não tem o vínculo: membro sem cargo é uma categoria da
     * distribuição, não uma linha que some. O nome vem junto do id para evitar uma segunda consulta.
     */
    private List<RefCount> byReference(MemberFilter filter, String attribute) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Member> root = query.from(Member.class);
        var joined = root.join(attribute, JoinType.LEFT);
        Path<UUID> id = joined.get("id");
        Path<String> name = joined.get("name");

        query.select(builder.tuple(id, name, builder.count(root)))
                .where(matching(root, query, builder, filter))
                .groupBy(id, name);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new RefCount(row.get(0, UUID.class), row.get(1, String.class),
                        row.get(2, Long.class)))
                .sorted(Comparator.comparingLong((RefCount row) -> row.count()).reversed()
                        .thenComparing(row -> row.id(), Comparator.nullsLast(BY_ID)))
                .toList();
    }

    private <E extends Enum<E>> List<EnumCount<E>> byEnum(MemberFilter filter, String attribute,
                                                          Class<E> type) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Member> root = query.from(Member.class);
        Path<E> value = root.get(attribute);

        query.select(builder.tuple(value, builder.count(root)))
                .where(matching(root, query, builder, filter))
                .groupBy(value);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new EnumCount<>(row.get(0, type), row.get(1, Long.class)))
                .sorted(Comparator.comparing(row -> row.value().ordinal()))
                .toList();
    }

    /**
     * Ocupação por cargo, no escopo do tenant: todos os cargos ativos, inclusive os sem ocupante,
     * e a contagem de ativos de cada um <b>sem nenhum filtro da requisição</b>.
     */
    List<RoleOccupancy> roleOccupancy() {
        Map<UUID, Long> occupants = countActiveBy("role");

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Role> root = query.from(Role.class);
        query.select(builder.tuple(root.get("id"), root.get("name"), root.get("maxSeats")))
                .where(builder.isTrue(root.get("active")));

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new RoleOccupancy(row.get(0, UUID.class), row.get(1, String.class),
                        row.get(2, Integer.class), occupants.getOrDefault(row.get(0, UUID.class), 0L)))
                .sorted(Comparator.comparing(role -> role.id(), BY_ID))
                .toList();
    }

    /** Diretorias ativas com zero membros ativos atribuídos diretamente a elas. */
    List<RefCount> emptyDepartments() {
        Map<UUID, Long> occupants = countActiveBy("department");

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Department> root = query.from(Department.class);
        query.select(builder.tuple(root.get("id"), root.get("name")))
                .where(builder.isTrue(root.get("active")));

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new RefCount(row.get(0, UUID.class), row.get(1, String.class),
                        occupants.getOrDefault(row.get(0, UUID.class), 0L)))
                .filter(row -> row.count() == 0)
                .sorted(Comparator.comparing(row -> row.id(), BY_ID))
                .toList();
    }

    /** Ativos por cargo ou diretoria no tenant inteiro; a chave nula (sem vínculo) não interessa aqui. */
    private Map<UUID, Long> countActiveBy(String attribute) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Member> root = query.from(Member.class);
        Path<UUID> id = root.get(attribute).get("id");

        query.select(builder.tuple(id, builder.count(root)))
                .where(builder.and(
                        builder.equal(root.get("status"), MemberStatus.ACTIVE),
                        builder.isNotNull(root.get(attribute))))
                .groupBy(id);

        return entityManager.createQuery(query).getResultList().stream()
                .collect(Collectors.toUnmodifiableMap(row -> row.get(0, UUID.class),
                        row -> row.get(1, Long.class)));
    }

    /** Soma sobre zero linhas é nula no SQL, e aqui a contagem conhecida sem ocorrências é zero. */
    private static long value(Long count) {
        return count == null ? 0 : count;
    }

    private static Expression<Long> countIf(CriteriaBuilder builder, Predicate condition) {
        return builder.sum(builder.<Long>selectCase().when(condition, 1L).otherwise(0L));
    }

    private static Predicate matching(Root<Member> root, CriteriaQuery<?> query, CriteriaBuilder builder,
                                      MemberFilter filter) {
        Specification<Member> specification = MemberSpecs.matching(filter);
        return specification.toPredicate(root, query, builder);
    }
}
