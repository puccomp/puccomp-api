package br.com.puccomp.api.recruitment.processes;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "O novo status é obrigatório")
        SelectionProcessStatus status
) { }
