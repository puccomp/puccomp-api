package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.organization.MemberDirectory.MemberProfile;
import br.com.puccomp.api.shared.reference.Standing;

import java.util.UUID;

public record MeResponse(UUID tenantId, String email, MemberView member) {

    public record MemberView(UUID id, String name, String course, String role, String department,
                             Standing standing) { }

    static MeResponse from(AuthPrincipal principal, MemberProfile profile) {
        MemberView member = profile == null ? null : new MemberView(
                profile.id(), profile.name(), profile.course(), profile.role(), profile.department(),
                principal.standing());
        return new MeResponse(principal.tenantId(), principal.email(), member);
    }
}
