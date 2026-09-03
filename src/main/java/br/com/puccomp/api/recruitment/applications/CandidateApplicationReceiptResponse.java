package br.com.puccomp.api.recruitment.applications;

import java.time.Instant;
import java.util.UUID;

public record CandidateApplicationReceiptResponse(UUID id, Instant submittedAt) {

    static CandidateApplicationReceiptResponse from(CandidateApplication application) {
        return new CandidateApplicationReceiptResponse(application.getId(), application.getCreatedAt());
    }
}
