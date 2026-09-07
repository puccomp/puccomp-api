package br.com.puccomp.api.identity.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @Schema(description = "Senha a definir; se a prévia trouxe account_exists=true, a senha atual da conta")
        @NotBlank String password,
        @NotBlank String name,
        @NotNull UUID courseId
) { }
