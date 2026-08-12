package br.com.puccomp.api.organization.departments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByIdAndActiveTrue(UUID id);
}
