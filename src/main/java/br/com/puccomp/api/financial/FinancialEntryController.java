package br.com.puccomp.api.financial;

import br.com.puccomp.api.financial.summary.FinancialSummaryResponse;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Financeiro")
@RestController
@RequestMapping("/v1/financial/entries")
@RequiredArgsConstructor
public class FinancialEntryController {

    private final FinancialEntryService service;

    @Operation(summary = "Registra um lançamento financeiro")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('financial:write')")
    @PostMapping
    public FinancialEntryResponse create(@RequestBody @Valid FinancialEntryRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Lista lançamentos financeiros por período e tipo")
    @PreAuthorize("hasAuthority('financial:read')")
    @GetMapping
    public Page<FinancialEntryResponse> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) FinancialEntryType type,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(from, to, type, pageable);
    }

    @Operation(summary = "Retrato agregado do extrato no período",
            description = "Responde de uma vez o que a listagem só responderia paginando tudo: "
                    + "quanto entrou, quanto saiu, o resultado do período, em que categorias o "
                    + "dinheiro se concentra e como o volume evoluiu mês a mês.\n\n"
                    + "Com from e to preenchidos, cada medida vem com o mesmo valor na janela "
                    + "anterior de igual largura — 30 dias se comparam com os 30 dias anteriores. "
                    + "Sem os dois extremos não há janela anterior, e previous vem nulo: "
                    + "comparação indisponível, que é diferente de zero.\n\n"
                    + "Descreve exatamente os lançamentos que a listagem irmã mostra — "
                    + "lançamento descartado fica de fora dos dois.")
    @ApiResponse(responseCode = "400", description = "Período inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('financial:read')")
    @GetMapping("/summary")
    public FinancialSummaryResponse summarize(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summarize(from, to);
    }

    @Operation(summary = "Busca um lançamento financeiro por ID")
    @ApiResponse(responseCode = "404", description = "Lançamento financeiro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('financial:read')")
    @GetMapping("/{id}")
    public FinancialEntryResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @Operation(summary = "Atualiza um lançamento financeiro")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lançamento financeiro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('financial:write')")
    @PatchMapping("/{id}")
    public FinancialEntryResponse update(@PathVariable UUID id,
                                         @RequestBody @Valid FinancialEntryUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Exclui um lançamento financeiro")
    @ApiResponse(responseCode = "404", description = "Lançamento financeiro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('financial:write')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
