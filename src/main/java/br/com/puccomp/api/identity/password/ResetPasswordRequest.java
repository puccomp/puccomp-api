package br.com.puccomp.api.identity.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        // O teto é o do BCrypt, que trunca em 72 bytes: sem ele o resto da senha seria ignorado
        // em silêncio e o usuário acharia que está protegido por algo que não conta.
        @NotBlank @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres") String password
) { }
