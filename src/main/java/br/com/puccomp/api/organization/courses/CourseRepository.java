package br.com.puccomp.api.organization.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByActiveTrueOrderByNameAsc();

    boolean existsByIdAndActiveTrue(UUID id);
    boolean existsByNameIgnoreCase(String name);
}
