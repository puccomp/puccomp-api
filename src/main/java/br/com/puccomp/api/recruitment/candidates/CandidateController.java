package br.com.puccomp.api.recruitment.candidates;

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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Candidatos")
@RestController
@RequestMapping("/v1/recruitment/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService service;

    @Operation(summary = "Cadastra um candidato")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Já existe um candidato com esse e-mail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PostMapping
    public CandidateResponse create(@RequestBody @Valid CandidateRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Atualiza um candidato por completo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Candidato não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Já existe um candidato com esse e-mail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:write')")
    @PutMapping("/{id}")
    public CandidateResponse update(@PathVariable UUID id, @RequestBody @Valid CandidateRequest request) {
        return service.update(id, request);
    }
}
