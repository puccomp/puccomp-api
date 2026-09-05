package br.com.puccomp.api.recruitment.applications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, UUID> {

    Page<CandidateApplication> findByProcessId(UUID processId, Pageable pageable);

    boolean existsByProcessIdAndEmailIgnoreCase(UUID processId, String email);

    @Query("select a.process.id as processId, count(a) as total, max(a.createdAt) as lastSubmittedAt "
            + "from CandidateApplication a where a.process.id in :processIds group by a.process.id")
    List<ProcessStatsRow> aggregateByProcessIds(@Param("processIds") Collection<UUID> processIds);

    interface ProcessStatsRow {
        UUID getProcessId();
        long getTotal();
        Instant getLastSubmittedAt();
    }
}
