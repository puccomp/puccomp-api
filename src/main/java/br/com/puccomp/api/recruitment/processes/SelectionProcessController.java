package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Processos Seletivos")
@RestController
@RequestMapping("/v1/recruitment/processes")
@RequiredArgsConstructor
public class SelectionProcessController {

    private final SelectionProcessService service;

    @Operation(summary = "Lista os processos seletivos da EJ")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public List<SelectionProcessResponse> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Busca um processo seletivo por ID")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/{processId}")
    public SelectionProcessResponse getById(@PathVariable UUID processId) {
        return service.findById(processId);
    }

    @Operation(summary = "Cria um novo processo seletivo. Nasce em DRAFT")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PostMapping
    public SelectionProcessResponse create(@RequestBody @Valid SelectionProcessRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Atualiza os dados de um processo seletivo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PutMapping("/{processId}")
    public SelectionProcessResponse update(@PathVariable UUID processId,
                                           @RequestBody @Valid SelectionProcessRequest request) {
        return service.update(processId, request);
    }

    @Operation(summary = "Avança o status do processo: DRAFT → OPEN → CLOSED → FINISHED. "
            + "CANCELLED é alcançável de qualquer estado não-final")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Transição de status inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PatchMapping("/{processId}/status")
    public SelectionProcessResponse changeStatus(@PathVariable UUID processId,
                                                 @RequestBody @Valid ChangeStatusRequest request) {
        return service.changeStatus(processId, request.status());
    }
}
