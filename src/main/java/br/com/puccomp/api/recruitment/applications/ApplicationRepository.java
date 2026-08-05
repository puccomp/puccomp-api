package br.com.puccomp.api.recruitment.applications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsBySelectionProcessIdAndEmailIgnoreCase(UUID selectionProcessId, String email);

    Page<Application> findBySelectionProcessId(UUID selectionProcessId, Pageable pageable);
}
