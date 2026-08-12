package br.com.puccomp.api.organization.roles;

import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, Integer maxSeats, boolean active) {

    static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getMaxSeats(),
                role.isActive()
        );
    }
}
