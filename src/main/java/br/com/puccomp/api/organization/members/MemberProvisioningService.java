package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.organization.courses.Course;
import br.com.puccomp.api.organization.roles.Role;
import br.com.puccomp.api.shared.reference.Standing;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberProvisioningService implements MemberProvisioning {

    private final MemberRepository members;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public boolean roleExists(UUID roleId) {
        return roleId != null && entityManager.find(Role.class, roleId) != null;
    }

    @Override
    @Transactional
    public UUID createMember(UUID accountId, String name, UUID courseId, UUID roleId, Standing standing) {
        Role role = roleId != null ? entityManager.find(Role.class, roleId) : null;
        var member = Member.builder()
                .accountId(accountId)
                .name(name)
                .course(entityManager.getReference(Course.class, courseId))
                .standing(standing)
                .status(MemberStatus.ACTIVE)
                .role(role)
                .department(role != null ? role.getDepartment() : null)
                .build();
        return members.save(member).getId();
    }
}
