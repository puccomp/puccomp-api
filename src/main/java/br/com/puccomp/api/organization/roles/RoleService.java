package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class RoleService {

    private final RoleRepository repository;

    RoleResponse create(RoleRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) 
            throw new ConflictException("Já existe um cargo com esse nome");
            
        var role = Role.builder()
                .name(name)
                .description(request.description())
                .hierarchyLevel(request.hierarchyLevel())
                .maxSeats(request.maxSeats())
                .build();
        return RoleResponse.from(repository.save(role));
    }

    Page<RoleResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(RoleResponse::from);
    }

    RoleResponse findById(UUID id) {
        return repository.findById(id)
                .map(RoleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado"));
    }
}
