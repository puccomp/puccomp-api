package br.com.puccomp.api.organization.departments;

import jakarta.validation.constraints.NotBlank;

public record DepartmentRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotBlank(message = "A descrição é obrigatória") String description
) {
}
