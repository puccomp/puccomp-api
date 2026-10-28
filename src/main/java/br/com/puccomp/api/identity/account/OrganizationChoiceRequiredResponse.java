package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.tenant.OrganizationView;
import br.com.puccomp.api.shared.exception.ErrorResponse;

import java.time.Instant;
import java.util.List;

public record OrganizationChoiceRequiredResponse(
        int status, String message, String traceId, Instant timestamp, List<OrganizationView> organizations) {

    static OrganizationChoiceRequiredResponse from(ErrorResponse error, List<OrganizationView> organizations) {
        return new OrganizationChoiceRequiredResponse(
                error.status(), error.message(), error.traceId(), error.timestamp(), organizations);
    }
}
