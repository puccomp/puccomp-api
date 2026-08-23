package br.com.puccomp.api.organization.roles;

import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, DepartmentSummary department,
                           Integer maxSeats, boolean active) {

    public record DepartmentSummary(UUID id, String name) { }

    static RoleResponse from(Role role) {
        var department = role.getDepartment();
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                department != null ? new DepartmentSummary(department.getId(), department.getName()) : null,
                role.getMaxSeats(),
                role.isActive()
        );
    }
}
