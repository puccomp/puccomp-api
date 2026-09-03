package br.com.puccomp.api.recruitment.processes;

import jakarta.validation.constraints.NotBlank;

public record SelectionProcessRequest(
        @NotBlank(message = "O título do processo seletivo é obrigatório")
        String title,
        String description
) { }
