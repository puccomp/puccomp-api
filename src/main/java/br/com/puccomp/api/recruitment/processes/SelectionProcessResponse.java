package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

public record SelectionProcessResponse(
        UUID id,
        String title,
        String description,
        SelectionProcessStatus status,
        boolean acceptingApplications,
        Instant opensAt,
        Instant closesAt,
        Instant resultAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SelectionProcessResponse from(SelectionProcess process, Instant at) {
        return new SelectionProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.effectiveStatus(at),
                process.isAcceptingApplications(at),
                process.getOpensAt(),
                process.getClosesAt(),
                process.getResultAt(),
                process.getCreatedAt(),
                process.getUpdatedAt()
        );
    }
}
