package br.com.puccomp.api.recruitment.applications;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * As agregações do resumo, em Criteria, reusando a mesma {@code Specification} da listagem.
 *
 * <p>Cada consulta reconstrói os predicados para o seu próprio root — um {@code Predicate}
 * pertence à árvore em que foi criado —, mas a interpretação dos filtros é a mesma da listagem;
 * é onde os dois divergiriam em silêncio. Nenhuma aplica paginação, ordenação ou fetch join:
 * eles descrevem a página, não o conjunto agregado.
 */
@Component
@RequiredArgsConstructor
class CandidateApplicationAggregations {

    private final EntityManager entityManager;

    record Totals(long total, long withCv, long withLinks, Instant firstSubmittedAt, Instant lastSubmittedAt) { }

    record CourseCount(UUID courseId, long count) { }

    record TermCount(Short term, long count) { }

    record DateCount(LocalDate date, long count) { }

    record ProcessCount(UUID processId, String title, Instant createdAt, long count) { }

    record CandidateCounts(long distinct, long returning) { }

    Totals totals(UUID processId, CandidateApplicationFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<CandidateApplication> root = query.from(CandidateApplication.class);

        Expression<Long> hasLink = builder.<Long>selectCase()
                .when(builder.isNotEmpty(CandidateApplicationSpecs.links(root)), 1L).otherwise(0L);

        query.select(builder.tuple(
                        builder.count(root),
                        builder.count(root.get("cvFileId")),
                        builder.sum(hasLink),
                        builder.least(root.<Instant>get("createdAt")),
                        builder.greatest(root.<Instant>get("createdAt"))))
                .where(matching(root, query, builder, processId, filter));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        return new Totals(
                value(row.get(0, Long.class)),
                value(row.get(1, Long.class)),
                value(row.get(2, Long.class)),
                row.get(3, Instant.class),
                row.get(4, Instant.class));
    }

    /**
     * Contagem decrescente, desempate pelo id em ordem textual — a mesma que o cliente vê na
     * resposta. A ordem natural de {@code UUID} compara dois longs com sinal e não a reproduz.
     */
    List<CourseCount> byCourse(UUID processId, CandidateApplicationFilter filter) {
        return grouped(processId, filter, (root, builder) -> root.get("courseId"), UUID.class).stream()
                .map(row -> new CourseCount(row.key(), row.count()))
                .sorted(Comparator.comparingLong((CourseCount row) -> row.count()).reversed()
                        .thenComparing(row -> row.courseId().toString()))
                .toList();
    }

    /** Ordem numérica crescente; quem não informou o período vem por último. */
    List<TermCount> byTerm(UUID processId, CandidateApplicationFilter filter) {
        return grouped(processId, filter, (root, builder) -> root.get("currentTerm"), Short.class).stream()
                .map(row -> new TermCount(row.key(), row.count()))
                .sorted(Comparator.comparing(row -> row.term(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Agrupa no fuso da EJ: em UTC toda inscrição entre 21h e a meia-noite cairia no dia seguinte,
     * justamente a faixa onde o pico de prazo acontece — que é o que este recorte existe para mostrar.
     */
    List<DateCount> byDay(UUID processId, CandidateApplicationFilter filter, ZoneId zone) {
        return bucketed(processId, filter, zone, "day_in_zone");
    }

    /**
     * A granularidade do histórico da EJ. Em anos de processos, a curva diária seria uma série sem
     * teto para responder sazonalidade — que é a única pergunta temporal que sobra fora de um prazo.
     */
    List<DateCount> byMonth(UUID processId, CandidateApplicationFilter filter, ZoneId zone) {
        return bucketed(processId, filter, zone, "month_in_zone");
    }

    /**
     * Título e criação do processo vêm no próprio agrupamento: a série é cronológica, e resolver os
     * rótulos depois custaria uma segunda consulta para o que o caminho já alcança. A navegação usa
     * os mesmos caminhos implícitos do filtro, e não um join explícito, para o Hibernate reaproveitar
     * uma junção só.
     */
    List<ProcessCount> byProcess(CandidateApplicationFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<CandidateApplication> root = query.from(CandidateApplication.class);

        Path<UUID> id = root.get("process").get("id");
        Path<String> title = root.get("process").get("title");
        Path<Instant> createdAt = root.get("process").get("createdAt");

        query.select(builder.tuple(id, title, createdAt, builder.count(root)))
                .where(matching(root, query, builder, null, filter))
                .groupBy(id, title, createdAt);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new ProcessCount(row.get(0, UUID.class), row.get(1, String.class),
                        row.get(2, Instant.class), value(row.get(3, Long.class))))
                .sorted(Comparator.comparing((ProcessCount row) -> row.createdAt()).reversed()
                        .thenComparing(row -> row.processId().toString()))
                .toList();
    }

    /**
     * Pessoas, e não inscrições: uma linha por e-mail distinto, reduzida aqui. "Quantas voltaram" é
     * a contagem dos grupos com mais de uma inscrição, e JPA não tem subconsulta no FROM para
     * fechar essa conta no banco. O que trafega é uma contagem por candidato distinto — não uma
     * linha por inscrição —, e as duas medidas saem da mesma consulta, sem chance de discordarem.
     */
    CandidateCounts candidates(UUID processId, CandidateApplicationFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<CandidateApplication> root = query.from(CandidateApplication.class);

        query.select(builder.count(root))
                .where(matching(root, query, builder, processId, filter))
                .groupBy(builder.lower(root.get("email")));

        List<Long> perCandidate = entityManager.createQuery(query).getResultList();
        return new CandidateCounts(perCandidate.size(),
                perCandidate.stream().filter(count -> count > 1).count());
    }

    private List<DateCount> bucketed(UUID processId, CandidateApplicationFilter filter, ZoneId zone,
                                     String bucket) {
        return grouped(processId, filter,
                (root, builder) -> builder.function(bucket, LocalDate.class,
                        root.get("createdAt"), builder.literal(zone.getId())),
                LocalDate.class).stream()
                .map(row -> new DateCount(row.key(), row.count()))
                .sorted(Comparator.comparing(row -> row.date()))
                .toList();
    }

    private record Group<K>(K key, long count) { }

    private interface Dimension {
        Expression<?> of(Root<CandidateApplication> root, CriteriaBuilder builder);
    }

    private <K> List<Group<K>> grouped(UUID processId, CandidateApplicationFilter filter,
                                       Dimension dimension, Class<K> keyType) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<CandidateApplication> root = query.from(CandidateApplication.class);
        Expression<?> key = dimension.of(root, builder);

        query.select(builder.tuple(key, builder.count(root)))
                .where(matching(root, query, builder, processId, filter))
                .groupBy(key);

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new Group<>(row.get(0, keyType), value(row.get(1, Long.class))))
                .toList();
    }

    private static Predicate matching(Root<CandidateApplication> root, CriteriaQuery<?> query,
                                      CriteriaBuilder builder, UUID processId,
                                      CandidateApplicationFilter filter) {
        return CandidateApplicationSpecs.matching(processId, filter).toPredicate(root, query, builder);
    }

    private static long value(Long count) {
        return count == null ? 0 : count;
    }
}
