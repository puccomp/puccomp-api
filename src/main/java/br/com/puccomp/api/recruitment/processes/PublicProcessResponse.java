package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

public record PublicProcessResponse(
        UUID id,
        String title,
        String description,
        Instant opensAt,
        Instant closesAt
) {
    static PublicProcessResponse from(SelectionProcess process) {
        return new PublicProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.getOpensAt(),
                process.getClosesAt());
    }
}
