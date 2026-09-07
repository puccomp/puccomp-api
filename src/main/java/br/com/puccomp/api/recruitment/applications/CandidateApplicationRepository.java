package br.com.puccomp.api.recruitment.applications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, UUID>,
        JpaSpecificationExecutor<CandidateApplication> {

    @Override
    @EntityGraph(attributePaths = "process")
    Page<CandidateApplication> findAll(Specification<CandidateApplication> spec, Pageable pageable);

    boolean existsByProcessIdAndEmailIgnoreCase(UUID processId, String email);

    @Query("select a.process.id as processId, count(a) as total, max(a.createdAt) as lastSubmittedAt "
            + "from CandidateApplication a where a.process.id in :processIds group by a.process.id")
    List<ProcessStatsRow> aggregateByProcessIds(@Param("processIds") Collection<UUID> processIds);

    @Query("""
            select count(a) as total,
                   count(a.cvFileId) as withCv,
                   sum(case when size(a.links) > 0 then 1 else 0 end) as withLinks,
                   min(a.createdAt) as firstSubmittedAt,
                   max(a.createdAt) as lastSubmittedAt
            from CandidateApplication a where a.process.id = :processId
            """)
    TotalsRow totalsByProcess(@Param("processId") UUID processId);

    @Query("""
            select a.courseId as courseId, count(a) as total
            from CandidateApplication a where a.process.id = :processId
            group by a.courseId order by count(a) desc
            """)
    List<CourseCountRow> countByCourse(@Param("processId") UUID processId);

    @Query("""
            select a.currentTerm as term, count(a) as total
            from CandidateApplication a where a.process.id = :processId
            group by a.currentTerm order by a.currentTerm asc nulls last
            """)
    List<TermCountRow> countByTerm(@Param("processId") UUID processId);

    /**
     * Nativa por precisar de {@code at time zone}: agrupar em UTC jogaria toda inscrição entre 21h e
     * meia-noite para o dia seguinte — justamente a faixa onde o pico de prazo acontece, que é o que
     * este recorte existe para mostrar.
     *
     * <p>Consulta nativa não passa pelo filtro de tenant do Hibernate, mas aqui o recorte por
     * {@code process_id} basta: a FK é composta {@code (tenant_id, process_id)}, então toda inscrição
     * desse processo é forçosamente do mesmo tenant — e quem chama já validou que o processo é seu.
     */
    @Query(value = """
            select (a.created_at at time zone :zone)::date as day, count(*) as total
            from candidate_applications a
            where a.process_id = :processId
            group by 1 order by 1
            """, nativeQuery = true)
    List<DayCountRow> countByDay(@Param("processId") UUID processId, @Param("zone") String zone);

    interface ProcessStatsRow {
        UUID getProcessId();
        long getTotal();
        Instant getLastSubmittedAt();
    }

    interface TotalsRow {
        long getTotal();
        long getWithCv();
        Long getWithLinks();
        Instant getFirstSubmittedAt();
        Instant getLastSubmittedAt();
    }

    interface CourseCountRow {
        UUID getCourseId();
        long getTotal();
    }

    interface TermCountRow {
        Short getTerm();
        long getTotal();
    }

    interface DayCountRow {
        LocalDate getDay();
        long getTotal();
    }
}
