package br.com.puccomp.api.recruitment.applications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, UUID> {

    Page<CandidateApplication> findByProcessId(UUID processId, Pageable pageable);
}
