package br.com.puccomp.api.organization.departments;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name, String description, boolean active) {

    static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.isActive()
        );
    }
}
