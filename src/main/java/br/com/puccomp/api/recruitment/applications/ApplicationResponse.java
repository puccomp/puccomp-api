package br.com.puccomp.api.recruitment.applications;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID processId,
        String fullName,
        String email,
        String phone,
        String university,
        String course,
        String currentTerm,
        String linkedinUrl,
        String portfolioUrl,
        ApplicationStatus status,
        boolean privacyConsent,
        Instant submittedAt
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getSelectionProcess().getId(),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                application.getUniversity(),
                application.getCourse(),
                application.getCurrentTerm(),
                application.getLinkedinUrl(),
                application.getPortfolioUrl(),
                application.getStatus(),
                application.isPrivacyConsent(),
                application.getCreatedAt()
        );
    }
}
