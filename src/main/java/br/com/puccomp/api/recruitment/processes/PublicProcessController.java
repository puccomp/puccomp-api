package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inscrição pública")
@SecurityRequirements
@RestController
@RequestMapping("/v1/public/{orgSlug}/processes")
@RequiredArgsConstructor
public class PublicProcessController {

    private final SelectionProcessService service;

    @Operation(summary = "Lista os processos seletivos abertos da EJ")
    @ApiResponse(responseCode = "404", description = "EJ não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<PublicProcessResponse> listOpen() {
        return service.listOpen();
    }

    @Operation(summary = "Detalha um processo seletivo aberto — é o que o candidato vê antes de se inscrever")
    @ApiResponse(responseCode = "404", description = "EJ ou processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{processId}")
    public PublicProcessResponse getOpenById(@PathVariable UUID processId) {
        return service.findOpenById(processId);
    }
}
