package br.com.puccomp.api.recruitment.candidates;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Tag(name = "Candidatos")
@RestController
@RequestMapping("/v1/recruitment/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService service;

    @Operation(summary = "Cria um novo candidato")
    @ApiResponse(responseCode = "201", description = "Candidato criado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "Email já cadastrado")
    @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    @PostMapping
    public CandidateResponse create(@RequestBody CandidateRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Atualiza um candidato")
    @ApiResponse(responseCode = "200", description = "Candidato atualizado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Candidato não encontrado")
    @ApiResponse(responseCode = "409", description = "Email já cadastrado")
    @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    @PatchMapping("/{id}")
    public CandidateResponse update(@PathVariable UUID id, @RequestBody CandidateRequest request) {
        return service.update(id, request);
    }

}
