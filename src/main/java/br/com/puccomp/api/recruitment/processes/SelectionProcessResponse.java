package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

public record SelectionProcessResponse(
        UUID id,
        String title,
        String description,
        SelectionProcessStatus status,
        boolean acceptingApplications,
        long applicationCount,
        Instant lastApplicationAt,
        Instant opensAt,
        Instant closesAt,
        Instant resultAt,
        Short minTerm,
        Short maxTerm,
        Instant createdAt,
        Instant updatedAt
) {
    static SelectionProcessResponse from(SelectionProcess process,
                                         ApplicationCounts.ApplicationStats stats, Instant at) {
        return new SelectionProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.effectiveStatus(at),
                process.isAcceptingApplications(at),
                stats.total(),
                stats.lastSubmittedAt(),
                process.getOpensAt(),
                process.getClosesAt(),
                process.getResultAt(),
                process.getMinTerm(),
                process.getMaxTerm(),
                process.getCreatedAt(),
                process.getUpdatedAt()
        );
    }
}
