package br.com.puccomp.api.identity.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull UUID courseId
) { }
