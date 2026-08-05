package br.com.puccomp.api.recruitment.processes;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

interface SelectionProcessRepository extends JpaRepository<SelectionProcess, UUID> {

    List<SelectionProcess> findAllByOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM selection_processes WHERE id = CAST(:id AS uuid)", nativeQuery = true)
    Optional<SelectionProcess> findByIdWithoutTenant(@Param("id") UUID id);
}
