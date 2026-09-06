package br.com.puccomp.api.recruitment.applications;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CandidateApplicationReceiptResponse(UUID id, @Schema(name = "submitted_at") Instant submittedAt) {

    static CandidateApplicationReceiptResponse from(CandidateApplication application) {
        return new CandidateApplicationReceiptResponse(application.getId(), application.getCreatedAt());
    }
}
