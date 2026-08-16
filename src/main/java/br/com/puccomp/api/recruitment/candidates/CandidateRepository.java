package br.com.puccomp.api.recruitment.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Candidate> findByEmailIgnoreCase(String email);
}
