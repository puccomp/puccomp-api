package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateApplicationResponse(
        UUID id,
        @Schema(name = "process_id") UUID processId,
        @Schema(name = "full_name") String fullName,
        String email,
        String phone,
        String course,
        @Schema(name = "current_term") String currentTerm,
        List<String> links,
        FileDownload cv,
        @Schema(name = "privacy_consent_at") Instant privacyConsentAt,
        @Schema(name = "submitted_at") Instant submittedAt
) {
    static CandidateApplicationResponse from(CandidateApplication application, FileDownload cv) {
        return new CandidateApplicationResponse(
                application.getId(),
                application.getProcess().getId(),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                application.getCourse(),
                application.getCurrentTerm(),
                application.getLinks(),
                cv,
                application.getPrivacyConsentAt(),
                application.getCreatedAt());
    }
}
