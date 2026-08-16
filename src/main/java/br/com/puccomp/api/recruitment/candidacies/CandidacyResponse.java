package br.com.puccomp.api.recruitment.candidacies;

import java.time.Instant;
import java.util.UUID;

public record CandidacyResponse(
        UUID id,
        UUID processId,
        String fullName,
        String email,
        String phone,
        String course,
        int currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        CandidacyStatus status,
        Instant privacyConsentAt,
        Instant submittedAt) {
    public static CandidacyResponse from(Candidacy candidacy) {
        return new CandidacyResponse(
                candidacy.getId(),
                candidacy.getProcess().getId(),
                candidacy.getCandidate().getFullName(),
                candidacy.getCandidate().getEmail(),
                candidacy.getCandidate().getPhone(),
                candidacy.getCourse(),
                candidacy.getCurrentTerm(),
                candidacy.getCandidate().getLinkedinUrl(),
                candidacy.getCandidate().getPortfolioUrl(),
                candidacy.getStatus(),
                candidacy.getPrivacyConsentAt(),
                candidacy.getCreatedAt());
    }
}
