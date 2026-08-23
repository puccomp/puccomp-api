package br.com.puccomp.api.organization.departments;

import br.com.puccomp.api.organization.DepartmentCatalog;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class DepartmentService implements DepartmentCatalog {

    private final DepartmentRepository repository;

    @Transactional
    DepartmentResponse create(DepartmentRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name))
            throw new ConflictException("Já existe uma diretoria com esse nome");

        var department = Department.builder()
                .name(name)
                .description(request.description())
                .build();
        return DepartmentResponse.from(repository.save(department));
    }

    @Transactional(readOnly = true)
    Page<DepartmentResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(DepartmentResponse::from);
    }

    @Transactional(readOnly = true)
    DepartmentResponse findById(UUID id) {
        return repository.findById(id)
                .map(DepartmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Diretoria não encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignable(UUID departmentId) {
        return departmentId != null && repository.existsByIdAndActiveTrue(departmentId);
    }
}
