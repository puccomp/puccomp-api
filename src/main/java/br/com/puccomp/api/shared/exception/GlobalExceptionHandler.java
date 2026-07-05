package br.com.puccomp.api.shared.exception;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
class GlobalExceptionHandler {

    private final Tracer tracer;

    GlobalExceptionHandler(Tracer tracer) { this.tracer = tracer; }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage(), currentTraceId());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleConflict(ConflictException ex) {
        return ErrorResponse.of(HttpStatus.CONFLICT.value(), ex.getMessage(), currentTraceId());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse handleUnauthorized(UnauthorizedException ex) {
        return ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), currentTraceId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), currentTraceId());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        InvalidFormatException invalidEnum = findInvalidEnum(ex);
        String message = invalidEnum != null
                ? invalidEnumMessage(invalidEnum)
                : "Corpo da requisição inválido ou ilegível";
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message, currentTraceId());
    }

    private static InvalidFormatException findInvalidEnum(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause())
            if (cause instanceof InvalidFormatException ife
                    && ife.getTargetType() != null && ife.getTargetType().isEnum())
                return ife;
        return null;
    }

    private static String invalidEnumMessage(InvalidFormatException ex) {
        var path = ex.getPath();
        String field = path.isEmpty() || path.get(path.size() - 1).getPropertyName() == null
                ? "campo"
                : path.get(path.size() - 1).getPropertyName();
        String accepted = Arrays.stream(ex.getTargetType().getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return "%s: valor inválido '%s'; use um de: %s".formatted(field, ex.getValue(), accepted);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message, currentTraceId());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNoResource(NoResourceFoundException ex) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Recurso não encontrado", currentTraceId());
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }
}
