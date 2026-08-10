package br.com.puccomp.api.recruitment.processes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SelectionProcessRepository extends JpaRepository<SelectionProcess, UUID> {

    List<SelectionProcess> findAllByOrderByCreatedAtDesc();

    List<SelectionProcess> findByStatusOrderByCreatedAtDesc(SelectionProcessStatus status);

    Optional<SelectionProcess> findByIdAndStatus(UUID id, SelectionProcessStatus status);
}
