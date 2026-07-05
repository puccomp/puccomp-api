package br.com.puccomp.api.shared.exception;

import java.time.Instant;

public record ErrorResponse(int status, String message, String traceId, Instant timestamp) {

    public static ErrorResponse of(int status, String message, String traceId) {
        return new ErrorResponse(status, message, traceId, Instant.now());
    }
}
