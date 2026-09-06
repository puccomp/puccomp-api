package br.com.puccomp.api.organization;

import br.com.puccomp.api.shared.reference.NamedRef;
import br.com.puccomp.api.shared.reference.Standing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberDirectory {

    Optional<MemberAccess> findAccess(UUID memberId);

    List<Membership> findMembershipsByAccount(UUID accountId);

    Optional<Membership> findMembership(UUID accountId, UUID tenantId);

    Optional<MemberProfile> findProfile(UUID memberId);

    /** Membros ativos com conta: exclui alumni, inativos e convites ainda pendentes. */
    List<ActiveMember> listActiveMembers();

    record Membership(UUID memberId, UUID tenantId, Standing standing) { }

    record MemberProfile(UUID id, String name, NamedRef course, NamedRef role, NamedRef department) { }

    record ActiveMember(UUID id, UUID accountId, UUID roleId, Standing standing) { }

    record MemberAccess(UUID roleId, boolean readOnly) { }
}
