package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.DepartmentCatalog;
import br.com.puccomp.api.organization.departments.Department;
import br.com.puccomp.api.organization.roles.Role;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberService {

    private final MemberRepository repository;
    private final DepartmentCatalog departmentCatalog;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    Page<MemberResponse> findAll(UUID departmentId, Pageable pageable) {
        Page<Member> members = departmentId == null
                ? repository.findAll(pageable)
                : repository.findAllByDepartmentId(departmentId, pageable);
        return members.map(MemberResponse::from);
    }

    @Transactional(readOnly = true)
    MemberResponse findById(UUID id) {
        return MemberResponse.from(findMember(id));
    }

    @Transactional
    MemberResponse retire(UUID id) {
        return transition(id, MemberStatus.ALUMNUS);
    }

    @Transactional
    MemberResponse reactivate(UUID id) {
        return transition(id, MemberStatus.ACTIVE);
    }

    @Transactional
    MemberResponse assign(UUID id, MemberAssignmentRequest request) {
        Member member = findMember(id);
        Role role = resolveRole(request.roleId());
        member.assign(role, resolveDepartment(role, request.departmentId()));
        return MemberResponse.from(member);
    }

    private MemberResponse transition(UUID id, MemberStatus status) {
        Member member = findMember(id);
        member.changeStatus(status);
        return MemberResponse.from(member);
    }

    private Member findMember(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
    }

    private Role resolveRole(UUID roleId) {
        if (roleId == null) return null;
        Role role = entityManager.find(Role.class, roleId);
        if (role == null || !role.isActive())
            throw new ResourceNotFoundException("Cargo não encontrado");
        return role;
    }

    private Department resolveDepartment(Role role, UUID departmentId) {
        Department roleDepartment = role != null ? role.getDepartment() : null;
        if (roleDepartment != null) {
            if (departmentId != null && !departmentId.equals(roleDepartment.getId()))
                throw new ConflictException("O cargo pertence a outra diretoria");
            return roleDepartment;
        }

        if (departmentId == null) return null;
        if (!departmentCatalog.isAssignable(departmentId))
            throw new ResourceNotFoundException("Departamento não encontrado");
        return entityManager.getReference(Department.class, departmentId);
    }
}
