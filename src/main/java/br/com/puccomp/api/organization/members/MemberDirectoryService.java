package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.shared.reference.NamedRef;
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
    public Optional<MemberAccess> findAccess(UUID memberId) {
        return members.findAccessById(memberId)
                .map(row -> new MemberAccess(row.getRoleId(), row.getStatus() == MemberStatus.ALUMNUS));
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
    public List<ActiveMember> listActiveMembers() {
        return members.findByStatus(MemberStatus.ACTIVE).stream()
                .filter(m -> m.getAccountId() != null)
                .map(m -> new ActiveMember(
                        m.getId(),
                        m.getAccountId(),
                        m.getRole() != null ? m.getRole().getId() : null,
                        m.getStanding()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberProfile> findProfile(UUID memberId) {
        return members.findById(memberId).map(m -> new MemberProfile(
                m.getId(),
                m.getName(),
                NamedRef.of(m.getCourse().getId(), m.getCourse().getName()),
                m.getRole() != null ? NamedRef.of(m.getRole().getId(), m.getRole().getName()) : null,
                m.getDepartment() != null
                        ? NamedRef.of(m.getDepartment().getId(), m.getDepartment().getName()) : null));
    }

    private Membership toMembership(MemberRepository.MembershipRow row) {
        return new Membership(row.getMemberId(), row.getTenantId(), Standing.valueOf(row.getStanding()));
    }
}
