package br.com.puccomp.api.identity.invitation;

import java.time.Instant;
import java.util.UUID;

public interface InvitationIssuer {

    IssuedInvitation issueForOwner(UUID tenantId, String email);

    record IssuedInvitation(UUID id, String acceptUrl, Instant expiresAt) { }
}
