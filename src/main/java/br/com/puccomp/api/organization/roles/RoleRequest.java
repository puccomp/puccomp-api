package br.com.puccomp.api.organization.roles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RoleRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotBlank(message = "A descrição é obrigatória") String description,
        @Positive(message = "O número de vagas deve ser maior que zero") Integer maxSeats
) {
}
