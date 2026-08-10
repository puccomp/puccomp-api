package br.com.puccomp.api.recruitment.candidacies;

import java.time.Instant;
import java.util.UUID;

/** Comprovante para o candidato anônimo: de propósito não ecoa a PII enviada. */
public record CandidacyReceiptResponse(
        UUID id,
        CandidacyStatus status,
        Instant submittedAt
) {
    static CandidacyReceiptResponse from(Candidacy candidacy) {
        return new CandidacyReceiptResponse(
                candidacy.getId(),
                candidacy.getStatus(),
                candidacy.getCreatedAt());
    }
}
