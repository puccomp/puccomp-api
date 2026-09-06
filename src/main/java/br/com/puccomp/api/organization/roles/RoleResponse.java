package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.reference.NamedRef;

import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, NamedRef department,
                           Integer maxSeats, boolean active) {

    static RoleResponse from(Role role) {
        var department = role.getDepartment();
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                department != null ? NamedRef.of(department.getId(), department.getName()) : null,
                role.getMaxSeats(),
                role.isActive()
        );
    }
}
