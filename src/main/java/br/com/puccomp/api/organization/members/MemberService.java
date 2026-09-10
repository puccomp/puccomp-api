package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.members.history.ReportWindow;
import br.com.puccomp.api.organization.members.history.MemberHistoryResponse;
import br.com.puccomp.api.organization.members.history.MemberHistoryService;
import br.com.puccomp.api.organization.members.history.MemberLifecycle;
import br.com.puccomp.api.organization.members.summary.MemberSummaryResponse;
import br.com.puccomp.api.organization.members.summary.MemberSummaryService;
import br.com.puccomp.api.organization.DepartmentCatalog;
import br.com.puccomp.api.organization.departments.Department;
import br.com.puccomp.api.organization.roles.Role;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.exception.ValidationException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberService {

    /** Centralizado no fuso da EJ, sem configuração por tenant: ainda não existe a primeira de fora. */
    private static final ZoneId EJ_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Set<String> CURRENT_STATE_FILTERS = Set.of(
            "department_id", "departmentId", "role_id", "course_id", "status", "standing",
            "has_role", "has_department");

    private final MemberRepository repository;
    private final DepartmentCatalog departmentCatalog;
    private final EntityManager entityManager;
    private final MemberSummaryService summaries;
    private final MemberLifecycle lifecycle;
    private final MemberHistoryService history;
    private final Clock clock;

    @Transactional(readOnly = true)
    Page<MemberResponse> findAll(MemberFilter filter, Pageable pageable) {
        return repository.findAll(MemberSpecs.matching(filter), pageable).map(MemberResponse::from);
    }

    MemberSummaryResponse summarize(MemberFilter filter, MemberSummaryService.ContextAccess access) {
        return summaries.summarize(filter, access);
    }

    /**
     * Os filtros de estado atual são recusados, não ignorados: aceitá-los em silêncio deixaria o
     * cliente convencido de que está lendo o turnover de uma diretoria. Um recorte histórico por
     * cargo ou diretoria exigiria histórico dessas atribuições, que ainda não existe — e usar a
     * atribuição de hoje para rotular o passado não está autorizado por tabela nenhuma.
     */
    MemberHistoryResponse history(String from, String to, Set<String> parameters) {
        Set<String> rejected = parameters.stream().filter(CURRENT_STATE_FILTERS::contains)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!rejected.isEmpty())
            throw new ValidationException(
                    "O relatório histórico descreve a EJ inteira e não aceita filtros de estado "
                            + "atual: " + String.join(", ", rejected));

        return history.report(ReportWindow.of(from, to, EJ_ZONE, clock.instant()));
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
        Member member = repository.findForStatusChange(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
        lifecycle.changeStatus(member, status);
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
