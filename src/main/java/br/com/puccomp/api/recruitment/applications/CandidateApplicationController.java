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
                    + "É null para inscrições sem currículo. Consulte novamente para renovar o acesso. "
                    + "applications_count e first_applied_at descrevem o histórico do e-mail na EJ "
                    + "inteira, e não este processo: nenhum filtro os restringe.\n\n"
                    + "Os filtros são combináveis: q (nome ou e-mail, ignorando acento), course_id, "
                    + "min_term, max_term, has_cv, has_links, from e to.")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<CandidateApplicationResponse> listByProcess(
            @PathVariable UUID processId,
            @ParameterObject CandidateApplicationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.listByProcess(processId, filter, pageable);
    }

    @Operation(summary = "Retrato agregado das inscrições de um processo seletivo",
            description = "Responde de uma vez o que a listagem só responderia paginando tudo: "
                    + "distribuição por curso e período, quantos anexaram currículo, e a curva de "
                    + "chegada por dia com o pico destacado.\n\n"
                    + "Aceita os mesmos oito filtros da listagem irmã (q, course_id, min_term, "
                    + "max_term, has_cv, has_links, from, to), com a mesma normalização e as mesmas "
                    + "rejeições: o resumo descreve exatamente as inscrições que a tabela ao lado "
                    + "mostra. "
                    + "from e to são instantes com offset, ambos inclusivos sobre created_at; "
                    + "faixa invertida não é erro, apenas não seleciona nada. "
                    + "page, size e sort não têm efeito: o resumo agrega todo o conjunto filtrado.\n\n"
                    + "Sem inscrições correspondentes: contagens zero, listas vazias e derivados "
                    + "nulos.\n\n"
                    + "É a visão de dentro do prazo: a curva diária, o pico e last_day_share só "
                    + "existem aqui. Para comparar processos, contar pessoas distintas e ver "
                    + "sazonalidade, use GET /v1/recruitment/applications/summary.")
    @ApiResponse(responseCode = "404", description = "Processo seletivo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/summary")
    public ApplicationSummaryResponse summary(
            @PathVariable UUID processId,
            @ParameterObject CandidateApplicationFilter filter,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.summarize(processId, filter);
    }
}
