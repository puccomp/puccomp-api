package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.identity.invitation.InvitationIssuer.IssuedInvitation;

import java.time.Instant;
import java.util.UUID;

public record ProvisionedOrganizationResponse(OrganizationView organization, Invitation invitation) {

    static ProvisionedOrganizationResponse from(Tenant tenant, IssuedInvitation invitation) {
        return new ProvisionedOrganizationResponse(
                OrganizationView.from(tenant),
                new Invitation(invitation.id(), invitation.acceptUrl(), invitation.expiresAt()));
    }

    public record Invitation(UUID id, String acceptUrl, Instant expiresAt) { }
}
