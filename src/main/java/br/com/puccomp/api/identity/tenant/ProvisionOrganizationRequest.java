package br.com.puccomp.api.identity.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProvisionOrganizationRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotBlank(message = "O slug é obrigatório") String slug,
        @NotBlank(message = "O email do dono é obrigatório")
        @Email(message = "Informe um email válido") String ownerEmail,
        @NotEmpty(message = "Informe ao menos um curso")
        List<@NotBlank(message = "O nome do curso é obrigatório") String> courses
) { }
