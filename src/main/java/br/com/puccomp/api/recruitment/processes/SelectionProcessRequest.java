package br.com.puccomp.api.recruitment.processes;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record SelectionProcessRequest(
        @NotBlank(message = "O título do processo seletivo é obrigatório")
        String title,

        String description,

        Instant startDate,

        Instant endDate
) { }
