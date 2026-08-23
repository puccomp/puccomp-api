package br.com.puccomp.api.organization.roles;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @EntityGraph(attributePaths = "department")
    Page<Role> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Role> findAllByDepartmentId(UUID departmentId, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Optional<Role> findById(UUID id);
}
