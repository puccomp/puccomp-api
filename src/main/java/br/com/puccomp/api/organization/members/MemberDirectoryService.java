package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberDirectoryService implements MemberDirectory {

    private final MemberRepository members;

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findRoleId(UUID memberId) {
        return members.findRoleIdById(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReadOnly(UUID memberId) {
        return members.findStatusById(memberId)
                .map(status -> status == MemberStatus.ALUMNUS)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> findMembershipsByAccount(UUID accountId) {
        return members.findMembershipsByAccountId(accountId).stream().map(this::toMembership).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> findMembership(UUID accountId, UUID tenantId) {
        return members.findMembership(accountId, tenantId).map(this::toMembership);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberProfile> findProfile(UUID memberId) {
        return members.findById(memberId).map(m -> new MemberProfile(
                m.getId(),
                m.getName(),
                m.getCourse(),
                m.getRole() != null ? m.getRole().getName() : null,
                m.getDepartment() != null ? m.getDepartment().getName() : null));
    }

    private Membership toMembership(MemberRepository.MembershipRow row) {
        return new Membership(row.getMemberId(), row.getTenantId(), Standing.valueOf(row.getStanding()));
    }
}
