package br.com.puccomp.api.recruitment.processes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record SelectionProcessRequest(
        @NotBlank(message = "O título do processo seletivo é obrigatório")
        String title,
        String description,
        @Schema(name = "opens_at") Instant opensAt,
        @Schema(name = "closes_at") Instant closesAt,
        @Schema(name = "result_at") Instant resultAt,
        @Schema(name = "min_term") @Min(value = 1, message = "O período mínimo é 1")
        @Max(value = 12, message = "O período máximo é 12") Short minTerm,
        @Schema(name = "max_term") @Min(value = 1, message = "O período mínimo é 1")
        @Max(value = 12, message = "O período máximo é 12") Short maxTerm
) { }
