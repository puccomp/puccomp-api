package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.organization.DepartmentCatalog;
import br.com.puccomp.api.organization.departments.Department;
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
class RoleService {

    private final RoleRepository repository;
    private final DepartmentCatalog departmentCatalog;
    private final EntityManager entityManager;

    @Transactional
    RoleResponse create(RoleRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name))
            throw new ConflictException("Já existe um cargo com esse nome");

        var role = Role.builder()
                .name(name)
                .description(request.description())
                .department(resolveDepartment(request.departmentId()))
                .maxSeats(request.maxSeats())
                .build();
        return RoleResponse.from(repository.save(role));
    }

    @Transactional(readOnly = true)
    Page<RoleResponse> findAll(UUID departmentId, Pageable pageable) {
        Page<Role> roles = departmentId == null
                ? repository.findAll(pageable)
                : repository.findAllByDepartmentId(departmentId, pageable);
        return roles.map(RoleResponse::from);
    }

    @Transactional(readOnly = true)
    RoleResponse findById(UUID id) {
        return RoleResponse.from(findRole(id));
    }

    @Transactional
    RoleResponse update(UUID id, RoleUpdateRequest request) {
        Role role = findRole(id);

        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id))
            throw new ConflictException("Já existe um cargo com esse nome");

        role.rename(name);
        role.changeDescription(request.description());
        role.changeDepartment(resolveDepartment(request.departmentId()));
        role.changeMaxSeats(request.maxSeats());
        role.changeActive(request.active());

        return RoleResponse.from(repository.save(role));
    }

    private Role findRole(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado"));
    }

    private Department resolveDepartment(UUID departmentId) {
        if (departmentId == null) return null;
        if (!departmentCatalog.isAssignable(departmentId))
            throw new ResourceNotFoundException("Departamento não encontrado");
        return entityManager.getReference(Department.class, departmentId);
    }
}
