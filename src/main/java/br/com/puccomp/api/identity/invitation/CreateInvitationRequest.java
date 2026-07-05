package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.shared.reference.Standing;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        Standing standing,
        UUID roleId
) { }
