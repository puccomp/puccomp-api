package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.shared.reference.NamedRef;
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
        NamedRef course,
        @Schema(name = "current_term") Short currentTerm,
        List<String> links,
        FileDownload cv,
        @Schema(name = "privacy_consent_at") Instant privacyConsentAt,
        @Schema(name = "submitted_at") Instant submittedAt
) {
    static CandidateApplicationResponse from(CandidateApplication application, FileDownload cv,
                                            String courseName) {
        return new CandidateApplicationResponse(
                application.getId(),
                application.getProcess().getId(),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                NamedRef.of(application.getCourseId(), courseName),
                application.getCurrentTerm(),
                application.getLinks(),
                cv,
                application.getPrivacyConsentAt(),
                application.getCreatedAt());
    }
}
