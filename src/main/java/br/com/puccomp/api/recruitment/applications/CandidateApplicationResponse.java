package br.com.puccomp.api.recruitment.applications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateApplicationResponse(
        UUID id,
        UUID processId,
        String fullName,
        String email,
        String phone,
        String course,
        String currentTerm,
        List<String> links,
        Instant privacyConsentAt,
        Instant submittedAt
) {
    static CandidateApplicationResponse from(CandidateApplication application) {
        return new CandidateApplicationResponse(
                application.getId(),
                application.getProcess().getId(),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                application.getCourse(),
                application.getCurrentTerm(),
                application.getLinks(),
                application.getPrivacyConsentAt(),
                application.getCreatedAt());
    }
}
