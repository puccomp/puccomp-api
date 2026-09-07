package br.com.puccomp.api.recruitment.processes;

import java.time.Instant;
import java.util.UUID;

/**
 * O que o candidato vê. Continua respondendo depois do prazo, com
 * {@code acceptingApplications} falso: quem guardou o link precisa conseguir ler quando encerrou e
 * quando sai o resultado, em vez de bater num 404 indistinguível de processo inexistente.
 */
public record PublicProcessResponse(
        UUID id,
        String title,
        String description,
        SelectionProcessStatus status,
        boolean acceptingApplications,
        Instant opensAt,
        Instant closesAt,
        Instant resultAt,
        Short minTerm,
        Short maxTerm
) {
    static PublicProcessResponse from(SelectionProcess process, Instant at) {
        return new PublicProcessResponse(
                process.getId(),
                process.getTitle(),
                process.getDescription(),
                process.effectiveStatus(at),
                process.isAcceptingApplications(at),
                process.getOpensAt(),
                process.getClosesAt(),
                process.getResultAt(),
                process.getMinTerm(),
                process.getMaxTerm());
    }
}
