package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.identity.invitation.InvitationIssuer.IssuedInvitation;

import java.time.Instant;
import java.util.UUID;

public record ProvisionedTenantResponse(UUID tenantId, String slug, Invitation invitation) {

    static ProvisionedTenantResponse from(Tenant tenant, IssuedInvitation invitation) {
        return new ProvisionedTenantResponse(
                tenant.getId(),
                tenant.getSlug(),
                new Invitation(invitation.id(), invitation.acceptUrl(), invitation.expiresAt()));
    }

    public record Invitation(UUID id, String acceptUrl, Instant expiresAt) { }
}
