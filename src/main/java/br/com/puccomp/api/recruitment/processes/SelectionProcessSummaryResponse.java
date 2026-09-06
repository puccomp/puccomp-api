package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

/**
 * A listagem não devolve {@code description}: é TEXT e pode carregar um edital inteiro, que ninguém
 * lê numa linha de tabela. Para isso existe o detalhe.
 */
public record SelectionProcessSummaryResponse(
        UUID id,
        String title,
        SelectionProcessStatus status,
        boolean acceptingApplications,
        long applicationCount,
        Instant lastApplicationAt,
        Instant opensAt,
        Instant closesAt,
        Instant resultAt,
        Instant createdAt
) {
    static SelectionProcessSummaryResponse from(SelectionProcess process,
                                                ApplicationCounts.ApplicationStats stats, Instant at) {
        return new SelectionProcessSummaryResponse(
                process.getId(),
                process.getTitle(),
                process.effectiveStatus(at),
                process.isAcceptingApplications(at),
                stats.total(),
                stats.lastSubmittedAt(),
                process.getOpensAt(),
                process.getClosesAt(),
                process.getResultAt(),
                process.getCreatedAt());
    }
}
