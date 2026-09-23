package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.shared.reference.NamedRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transitório: o que as listagens REST ainda devolvem, com a URL do currículo assinada linha a
 * linha, porque o front abre o arquivo a partir dela. Sai quando ele passar a pedir
 * {@code GET /v1/recruitment/applications/{id}/cv}, e as listagens adotam
 * {@link CandidateApplicationResponse}.
 */
public record SignedCandidateApplicationResponse(
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
                description = "Quantas inscrições este e-mail tem na EJ, contando esta. Descreve o "
                        + "histórico inteiro: nenhum filtro da consulta o restringe")
        long applicationsCount,

        @Schema(name = "first_applied_at",
                description = "Primeira vez que este e-mail se inscreveu na EJ")
        Instant firstAppliedAt
) {
    static SignedCandidateApplicationResponse from(CandidateApplicationResponse response, FileDownload cv) {
        return new SignedCandidateApplicationResponse(
                response.id(),
                response.process(),
                response.fullName(),
                response.email(),
                response.phone(),
                response.course(),
                response.currentTerm(),
                response.links(),
                cv,
                response.privacyConsentAt(),
                response.submittedAt(),
                response.applicationsCount(),
                response.firstAppliedAt());
    }
}
