package br.com.puccomp.api.organization.roles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record RoleUpdateRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotBlank(message = "A descrição é obrigatória") String description,
        UUID departmentId,
        @Positive(message = "O número de vagas deve ser maior que zero") Integer maxSeats,
        @NotNull(message = "Informe se o cargo está ativo") Boolean active
) {
}
