package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

@Tag(name = "Inscrições")
@RestController
@RequestMapping("/v1/recruitment/processes/{processId}/applications")
@RequiredArgsConstructor
public class CandidateApplicationController {

    private final CandidateApplicationService service;

    @Operation(summary = "Lista as inscrições recebidas em um processo seletivo",
            description = "cv contém metadados e download_url pré-assinada, válida até download_expires_at. "
                    + "É null para inscrições sem currículo. Consulte novamente para renovar o acesso.")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<CandidateApplicationResponse> listByProcess(
            @PathVariable UUID processId,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.listByProcess(processId, pageable);
    }
}
