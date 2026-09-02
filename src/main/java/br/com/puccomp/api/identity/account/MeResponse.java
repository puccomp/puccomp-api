package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.tenant.OrganizationView;
import br.com.puccomp.api.organization.MemberDirectory.MemberProfile;
import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.reference.Standing;

import java.util.List;
import java.util.UUID;

/**
 * Contexto da sessão: quem está autenticado, por qual EJ e o que pode fazer.
 *
 * <p>As {@code permissions} são as efetivas — já com PAT rebaixado a somente-leitura e OWNER
 * recebendo tudo. Essas duas regras só existem no filtro de autenticação, então nenhum cliente
 * consegue recalculá-las: publicá-las aqui evita que a UI e a API discordem sobre o mesmo acesso.
 */
public record MeResponse(OrganizationView organization, String email, MemberView member,
                         List<String> permissions) {

    public record MemberView(UUID id, String name, NamedRef course, NamedRef role, NamedRef department,
                             Standing standing) { }

    static MeResponse from(AuthPrincipal principal, OrganizationView organization, MemberProfile profile,
                           List<String> permissions) {
        MemberView member = profile == null ? null : new MemberView(
                profile.id(), profile.name(), profile.course(), profile.role(), profile.department(),
                principal.standing());
        return new MeResponse(organization, principal.email(), member, permissions);
    }
}
