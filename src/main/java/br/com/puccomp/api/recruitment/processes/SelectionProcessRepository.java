package br.com.puccomp.api.recruitment.processes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SelectionProcessRepository extends JpaRepository<SelectionProcess, UUID> {

    List<SelectionProcess> findByStatusOrderByCreatedAtDesc(SelectionProcessStatus status);

    Optional<SelectionProcess> findByIdAndStatus(UUID id, SelectionProcessStatus status);

    Page<SelectionProcess> findByStatus(SelectionProcessStatus status, Pageable pageable);

    /** OPEN de verdade: gravado como OPEN e ainda dentro do prazo. */
    @Query("""
            select p from SelectionProcess p
            where p.status = br.com.puccomp.api.recruitment.processes.SelectionProcessStatus.OPEN
              and (p.closesAt is null or p.closesAt > :now)
            """)
    Page<SelectionProcess> findEffectivelyOpen(@Param("now") Instant now, Pageable pageable);

    /** IN_REVIEW inclui quem ainda está gravado como OPEN mas já passou do prazo. */
    @Query("""
            select p from SelectionProcess p
            where p.status = br.com.puccomp.api.recruitment.processes.SelectionProcessStatus.IN_REVIEW
               or (p.status = br.com.puccomp.api.recruitment.processes.SelectionProcessStatus.OPEN
                   and p.closesAt is not null and p.closesAt <= :now)
            """)
    Page<SelectionProcess> findEffectivelyInReview(@Param("now") Instant now, Pageable pageable);
}
