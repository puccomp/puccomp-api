package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.shared.reference.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull Course course
) { }
