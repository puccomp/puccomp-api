package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

public record SelectionProcessResponse(
        UUID id,
        String title,
        String description,
        SelectionProcessStatus status,
        Instant startDate,
        Instant endDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static SelectionProcessResponse from(SelectionProcess process) {
        return new SelectionProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.getStatus(),
                process.getStartDate(),
                process.getEndDate(),
                process.getCreatedAt(),
                process.getUpdatedAt()
        );
    }
}
