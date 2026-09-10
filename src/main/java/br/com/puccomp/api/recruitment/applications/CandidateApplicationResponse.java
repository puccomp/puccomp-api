package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateApplicationResponse(
        UUID id,
        NamedRef process,
        @Schema(name = "full_name") String fullName,
        String email,
        String phone,
        NamedRef course,
        @Schema(name = "current_term") Short currentTerm,
        List<String> links,
        FileDownload cv,
        @Schema(name = "privacy_consent_at") Instant privacyConsentAt,
        @Schema(name = "submitted_at") Instant submittedAt,

        @Schema(name = "applications_count",
                description = "Quantas inscrições este e-mail tem na EJ, contando esta. Maior que 1 "
                        + "é candidato reincidente — só acontece entre processos, porque o mesmo "
                        + "e-mail não se inscreve duas vezes no mesmo. "
                        + "Descreve o histórico inteiro: nenhum filtro da consulta o restringe")
        long applicationsCount,

        @Schema(name = "first_applied_at",
                description = "Primeira vez que este e-mail se inscreveu na EJ; igual a submitted_at "
                        + "quando applications_count é 1")
        Instant firstAppliedAt
) {

    /** O histórico do e-mail na EJ, resolvido em lote para a página inteira. */
    record History(long applicationsCount, Instant firstAppliedAt) { }

    static CandidateApplicationResponse from(CandidateApplication application, FileDownload cv,
                                            String courseName, History history) {
        return new CandidateApplicationResponse(
                application.getId(),
                NamedRef.of(application.getProcess().getId(), application.getProcess().getTitle()),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                NamedRef.of(application.getCourseId(), courseName),
                application.getCurrentTerm(),
                application.getLinks(),
                cv,
                application.getPrivacyConsentAt(),
                application.getCreatedAt(),
                history.applicationsCount(),
                history.firstAppliedAt());
    }
}
