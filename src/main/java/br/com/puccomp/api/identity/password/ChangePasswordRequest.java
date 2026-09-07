package br.com.puccomp.api.identity.password;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @ValidPassword String newPassword
) { }
