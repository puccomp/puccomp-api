package br.com.puccomp.api.organization.departments;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Diretorias")
@RestController
@RequestMapping("/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @Operation(summary = "Cria uma nova diretoria")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Já existe uma diretoria com esse nome",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('departments:write')")
    @PostMapping
    public DepartmentResponse create(@RequestBody @Valid DepartmentRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Lista todas as diretorias paginadas")
    @PreAuthorize("hasAuthority('departments:read')")
    @GetMapping
    public Page<DepartmentResponse> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = {"name", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.findAll(pageable);
    }

    @Operation(summary = "Busca diretoria por ID")
    @ApiResponse(responseCode = "404", description = "Diretoria não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('departments:read')")
    @GetMapping("/{id}")
    public DepartmentResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }
}
