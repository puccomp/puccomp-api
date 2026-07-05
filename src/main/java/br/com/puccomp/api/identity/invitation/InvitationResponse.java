package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.shared.reference.Standing;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID id, String email, Standing standing, String tokenPrefix,
                                 Instant expiresAt, InvitationStatus status) {

    static InvitationResponse from(Invitation invitation, Instant now) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStanding(),
                invitation.getTokenPrefix(),
                invitation.getExpiresAt(),
                invitation.status(now));
    }
}
