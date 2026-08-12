package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Cargos")
@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @Operation(summary = "Cria um novo cargo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Departamento não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Já existe um cargo com esse nome",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles:write')")
    @PostMapping
    public RoleResponse create(@RequestBody @Valid RoleRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Lista todos os cargos paginados")
    @PreAuthorize("hasAuthority('roles:read')")
    @GetMapping
    public Page<RoleResponse> getAll(
            @Parameter(description = "Filtra os cargos de uma diretoria; id desconhecido devolve página vazia")
            @RequestParam(required = false) UUID departmentId,
            Pageable pageable) {
        return service.findAll(departmentId, pageable);
    }

    @Operation(summary = "Busca cargo por ID")
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('roles:read')")
    @GetMapping("/{id}")
    public RoleResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }
}
