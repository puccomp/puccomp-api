package br.com.puccomp.api.organization.courses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

import java.util.List;

@Tag(name = "Cursos")
@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService service;

    @Operation(summary = "Lista os cursos que a EJ aceita")
    @GetMapping
    public List<CourseResponse> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Busca um curso por ID")
    @ApiResponse(responseCode = "404", description = "Curso não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public CourseResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @Operation(summary = "Atualiza um curso (renomeia e/ou ativa/desativa)")
    @ApiResponse(responseCode = "404", description = "Curso não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Já existe um curso com esse nome",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('courses:write')")
    @PatchMapping("/{id}")
    public CourseResponse update(@PathVariable UUID id, @RequestBody @Valid CourseUpdateRequest request) {
        return service.update(id, request);
    }
}
