package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.recruitment.applications.ApplicationResponse;
import br.com.puccomp.api.recruitment.applications.CreateApplicationRequest;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Processos Seletivos")
@RestController
@RequestMapping("/v1/recruitment/processes")
@RequiredArgsConstructor
public class SelectionProcessController {

    private final SelectionProcessService service;

    @Operation(summary = "Lista todos os processos seletivos da empresa júnior")
    @GetMapping
    public List<SelectionProcessResponse> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Busca um processo seletivo por ID")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{processId}")
    public SelectionProcessResponse getById(@PathVariable UUID processId) {
        return service.findById(processId);
    }

    @Operation(summary = "Cria um novo processo seletivo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PostMapping
    public SelectionProcessResponse create(@RequestBody @Valid SelectionProcessRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Atualiza os dados de um processo seletivo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PutMapping("/{processId}")
    public SelectionProcessResponse update(@PathVariable UUID processId,
            @RequestBody @Valid SelectionProcessRequest request) {
        return service.update(processId, request);
    }

    @Operation(summary = "Altera o status de um processo seletivo")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PatchMapping("/{processId}/status")
    public SelectionProcessResponse changeStatus(@PathVariable UUID processId,
            @RequestParam SelectionProcessStatus status) {
        return service.changeStatus(processId, status);
    }

    @Operation(summary = "Enviar candidaturas para o processo seletivo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Candidatura já enviada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{processId}/applications")
    public ApplicationResponse submitApplications(@PathVariable UUID processId, @RequestBody @Valid CreateApplicationRequest request) {
        return service.submitApplications(processId, request);
    }

    @Operation(summary = "Listar Candidaturas de um processo seletivo")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/{processId}/applications")
    public Page<ApplicationResponse> listApplications(@PathVariable UUID processId, Pageable pageable) {
        return service.listApplications(processId, pageable);
    }
}
