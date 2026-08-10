package br.com.puccomp.api.recruitment.candidacies;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CandidacyRepository extends JpaRepository<Candidacy, UUID> {

    boolean existsByProcessIdAndEmailIgnoreCase(UUID processId, String email);

    Page<Candidacy> findByProcessId(UUID processId, Pageable pageable);
}
