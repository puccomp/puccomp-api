package br.com.puccomp.api.recruitment.applications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, UUID> {

    @EntityGraph(attributePaths = "process")
    Page<CandidateApplication> findByProcessId(UUID processId, Pageable pageable);

    /** Toda a EJ: o filtro de tenant do Hibernate já recorta, então não há where explícito. */
    @EntityGraph(attributePaths = "process")
    Page<CandidateApplication> findBy(Pageable pageable);

    /**
     * {@code searchName} é coluna gerada — sem acento e minúscula — com índice GIN de trigrama, e o
     * termo chega normalizado igual. O {@code escape} impede que % ou _ digitados virem curinga.
     */
    @EntityGraph(attributePaths = "process")
    @Query("""
            select a from CandidateApplication a
            where a.process.id = :processId
              and (a.searchName like :term escape '\\' or lower(a.email) like :term escape '\\')
            """)
    Page<CandidateApplication> searchByProcessId(@Param("processId") UUID processId,
                                                 @Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = "process")
    @Query("""
            select a from CandidateApplication a
            where a.searchName like :term escape '\\' or lower(a.email) like :term escape '\\'
            """)
    Page<CandidateApplication> search(@Param("term") String term, Pageable pageable);

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
