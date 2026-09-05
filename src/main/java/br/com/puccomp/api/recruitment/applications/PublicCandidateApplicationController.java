package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileUpload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.exception.InvalidFileException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.io.IOException;

@Tag(name = "Inscrição pública")
@SecurityRequirements
@RestController
@RequestMapping("/v1/public/{orgSlug}/processes/{processId}/applications")
@RequiredArgsConstructor
public class PublicCandidateApplicationController {

    private final CandidateApplicationService service;

    @Operation(summary = "Envia uma inscrição para um processo seletivo aberto")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "EJ não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Processo fechado ou inscrição já enviada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateApplicationReceiptResponse submit(
            @PathVariable UUID processId,
            @RequestBody @Valid SubmitCandidateApplicationRequest request) {
        return service.submit(processId, request);
    }

    @Operation(summary = "Envia inscrição com currículo PDF privado",
            description = "Multipart com application (JSON application/json) e cv (application/pdf). "
                    + "PDF obrigatório nesta variante, até 5 MiB e 20 páginas, sem criptografia, scripts, "
                    + "formulários ou anexos. O comprovante público não contém URL de download.")
    @ApiResponse(responseCode = "400", description = "Dados, multipart ou PDF inválidos")
    @ApiResponse(responseCode = "413", description = "Arquivo ou requisição excede o limite")
    @ApiResponse(responseCode = "409", description = "Processo fechado ou inscrição duplicada")
    @ApiResponse(responseCode = "503", description = "Storage ou verificação de segurança indisponível")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateApplicationReceiptResponse submitWithCv(
            @PathVariable UUID processId,
            @RequestPart("application") @Valid SubmitCandidateApplicationRequest request,
            @RequestPart("cv") MultipartFile cv,
            HttpServletRequest servletRequest) throws ServletException, IOException {
        // Rejeita nomes desconhecidos e partes duplicadas em vez de ignorar arquivos adicionais.
        var parts = servletRequest.getParts();
        if (parts.size() != 2 || parts.stream().filter(p -> p.getName().equals("application")).count() != 1
                || parts.stream().filter(p -> p.getName().equals("cv")).count() != 1)
            throw new InvalidFileException("Envie exatamente as partes application e cv");
        return service.submit(processId, request,
                new FileUpload(cv.getOriginalFilename(), cv.getContentType(), cv.getSize(), cv::getInputStream));
    }
}
