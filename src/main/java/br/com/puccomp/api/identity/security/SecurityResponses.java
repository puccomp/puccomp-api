package br.com.puccomp.api.identity.security;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

final class SecurityResponses {

    private SecurityResponses() { }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, Tracer tracer,
                      HttpStatus status, String message) throws IOException {
        Span span = tracer.currentSpan();
        String traceId = span != null ? span.context().traceId() : null;

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status.value(), message, traceId));
    }
}
