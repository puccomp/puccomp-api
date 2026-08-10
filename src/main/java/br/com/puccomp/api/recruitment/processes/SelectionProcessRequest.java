package br.com.puccomp.api.recruitment.processes;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record SelectionProcessRequest(
        @NotBlank(message = "O título do processo seletivo é obrigatório")
        String title,

        String description,

        Instant opensAt,

        Instant closesAt
) {
    @AssertTrue(message = "A data de término deve ser posterior à de início")
    public boolean isPeriodConsistent() {
        return opensAt == null || closesAt == null || closesAt.isAfter(opensAt);
    }
}
