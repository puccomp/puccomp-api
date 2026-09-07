package br.com.puccomp.api.identity.password;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @ValidPassword String password
) { }
