package br.com.puccomp.api.organization;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberDirectory {

    Optional<UUID> findRoleId(UUID memberId);

    boolean isReadOnly(UUID memberId);

    List<Membership> findMembershipsByAccount(UUID accountId);

    Optional<Membership> findMembership(UUID accountId, UUID tenantId);

    Optional<MemberProfile> findProfile(UUID memberId);

    record Membership(UUID memberId, UUID tenantId, Standing standing) { }

    record MemberProfile(UUID id, String name, String course, String role, String department) { }
}
