package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Processos Seletivos")
@RestController
@RequestMapping("/v1/recruitment/processes")
@RequiredArgsConstructor
public class SelectionProcessController {

    private final SelectionProcessQueryService queries;
    private final SelectionProcessService service;

    @Operation(summary = "Lista os processos seletivos da EJ",
            description = "O filtro status casa com o status efetivo: OPEN traz só quem está dentro do "
                    + "prazo, e IN_REVIEW inclui quem ainda está gravado como OPEN mas já venceu. "
                    + "q busca no título ignorando acento e caixa; termos com menos de 2 caracteres "
                    + "são desconsiderados.")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<SelectionProcessListItemResponse> getAll(
            @RequestParam(required = false) SelectionProcessStatus status,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.findAll(status, q, pageable);
    }

    @Operation(summary = "Busca um processo seletivo por ID",
            description = "Traz application_count e last_application_at, como a listagem. As "
                    + "alterações devolvem o processo sem esses dois campos.")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/{processId}")
    public SelectionProcessDetailResponse getById(@PathVariable UUID processId) {
        return queries.findById(processId);
    }

    @Operation(summary = "Cria um novo processo seletivo. Nasce em DRAFT")
    @ApiResponse(responseCode = "201", description = "Processo criado",
            headers = @Header(name = "Location", description = "Caminho do detalhe do processo criado"))
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PostMapping
    public ResponseEntity<SelectionProcessResponse> create(@RequestBody @Valid SelectionProcessRequest request) {
        SelectionProcessResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/v1/recruitment/processes/" + created.id())).body(created);
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

    @Operation(summary = "Avança o status do processo: DRAFT → OPEN → CLOSED. "
            + "CANCELLED é alcançável antes do fechamento",
            description = "Pedir o status em que o processo já está responde 200 sem efeito nenhum — "
                    + "inclusive sem reenviar o aviso de fase aos candidatos.")
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
