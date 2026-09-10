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

    interface ProcessStatsRow {
        UUID getProcessId();
        long getTotal();
        Instant getLastSubmittedAt();
    }

    /**
     * O histórico de cada e-mail na EJ inteira, para a página que está sendo apresentada. Agrupa
     * por {@code lower(email)} porque a unicidade por processo também ignora caixa, e sem
     * {@code process_id} no recorte: reincidência só existe entre processos.
     */
    @Query("select lower(a.email) as email, count(a) as total, min(a.createdAt) as firstAppliedAt "
            + "from CandidateApplication a where lower(a.email) in :emails group by lower(a.email)")
    List<CandidateHistoryRow> aggregateByEmails(@Param("emails") Collection<String> emails);

    interface CandidateHistoryRow {
        String getEmail();
        long getTotal();
        Instant getFirstAppliedAt();
    }

}
