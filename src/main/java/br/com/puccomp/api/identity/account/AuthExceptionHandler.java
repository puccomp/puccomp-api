package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
@RequiredArgsConstructor
class AuthExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(OrganizationChoiceRequiredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    OrganizationChoiceRequiredResponse handleOrganizationChoice(OrganizationChoiceRequiredException ex) {
        var span = tracer.currentSpan();
        var traceId = span == null ? null : span.context().traceId();
        return OrganizationChoiceRequiredResponse.from(
                ErrorResponse.of(409, ex.getMessage(), traceId), ex.getOrganizations());
    }
}
