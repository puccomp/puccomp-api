package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

/**
 * O processo em si, como as alterações o devolvem. Não carrega a contagem de inscrições: ela é de
 * outro agregado, e quem precisa dela lê o detalhe.
 */
public record SelectionProcessResponse(
        UUID id,
        String title,
        String description,
        SelectionProcessStatus status,
        boolean acceptingApplications,
        Instant opensAt,
        Instant closesAt,
        Instant resultAt,
        Short minTerm,
        Short maxTerm,
        Instant createdAt,
        Instant updatedAt
) {
    static SelectionProcessResponse from(SelectionProcess process, Instant at) {
        return new SelectionProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.effectiveStatus(at),
                process.isAcceptingApplications(at),
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
