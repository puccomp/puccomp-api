package br.com.puccomp.api.identity.token;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record CreatePatRequest(
        @NotBlank(message = "O nome do token é obrigatório") String name,
        List<String> scopes,
        Instant expiresAt
) {
}
