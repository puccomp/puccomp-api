package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.recruitment.applications.summary.ApplicationHistorySummaryResponse;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.UUID;

@Tag(name = "Inscrições")
@RestController
@RequestMapping("/v1/recruitment/applications")
@RequiredArgsConstructor
public class CandidateSearchController {

    private final CandidateApplicationService service;

    @Operation(summary = "Busca inscrições em todos os processos seletivos da EJ",
            description = "Responde \"essa pessoa já se inscreveu antes?\": diferente da listagem por "
                    + "processo, varre o histórico inteiro da EJ. q casa nome ou e-mail ignorando acento "
                    + "e caixa; sem q, devolve o histórico completo paginado. Aceita os mesmos filtros "
                    + "da listagem por processo — course_id, min_term, max_term, has_cv, has_links, "
                    + "from e to — mais process_id, que recorta um processo sem trocar de rota.\n\n"
                    + "Cada linha já responde a reincidência sem varrer as páginas: applications_count "
                    + "maior que 1 é quem voltou, e first_applied_at diz desde quando. Os dois olham "
                    + "para a EJ inteira, e nenhum filtro desta consulta os restringe.\n\n"
                    + "cv.download_url nesta listagem é transitória e vai sair: para abrir o arquivo, "
                    + "use GET /v1/recruitment/applications/{applicationId}/cv.")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<SignedCandidateApplicationResponse> search(
            @ParameterObject CandidateApplicationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.searchAcrossProcessesSigned(filter, pageable);
    }

    @Operation(summary = "Busca uma inscrição por ID",
            description = "cv descreve o currículo — nome, tipo e tamanho — sem dar acesso a ele; o "
                    + "arquivo sai de GET /v1/recruitment/applications/{applicationId}/cv. "
                    + "applications_count e first_applied_at descrevem o histórico do e-mail na EJ "
                    + "inteira.")
    @ApiResponse(responseCode = "404", description = "Inscrição não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/{applicationId}")
    public CandidateApplicationResponse getById(@PathVariable UUID applicationId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.findById(applicationId);
    }

    @Operation(summary = "Gera o acesso temporário ao currículo de uma inscrição",
            description = "Cada chamada assina uma URL nova, que permite GET direto no armazenamento, "
                    + "sem Authorization, até download_expires_at. Chame no momento de abrir o "
                    + "arquivo, e não ao montar a tela: a URL vence em minutos.")
    @ApiResponse(responseCode = "404", description = "Inscrição não encontrada ou sem currículo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Armazenamento de arquivos indisponível",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/{applicationId}/cv")
    public FileDownload cv(@PathVariable UUID applicationId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.cvOf(applicationId);
    }

    @Operation(summary = "Retrato agregado das inscrições da EJ inteira",
            description = "Responde o que nenhum processo isolado responde: quantas pessoas "
                    + "diferentes já passaram pelo funil, quantas voltaram, como o volume evoluiu "
                    + "entre processos, em que meses as inscrições chegam e que cursos do catálogo "
                    + "a EJ nunca alcançou.\n\n"
                    + "Aceita os mesmos nove filtros da busca irmã (q, process_id, course_id, "
                    + "min_term, max_term, has_cv, has_links, from, to), com a mesma normalização e "
                    + "as mesmas rejeições: o resumo descreve exatamente as inscrições que a tabela "
                    + "ao lado mostra. page, size e sort não têm efeito: o resumo agrega todo o "
                    + "conjunto filtrado.\n\n"
                    + "total e as distribuições estão em inscrições e fecham entre si; candidates "
                    + "está em pessoas e não fecha com nenhuma delas — quem se inscreveu em três "
                    + "processos é três inscrições e uma pessoa.\n\n"
                    + "Sem inscrições correspondentes: contagens zero, listas vazias e derivados "
                    + "nulos. EJ sem nenhum processo também é 200, não 404.\n\n"
                    + "É a visão entre processos. process_id recorta um deles para o drill-down do "
                    + "painel, mas não substitui o resumo do processo: assim filtrado, "
                    + "candidates.distinct iguala total e returning é sempre zero — o mesmo e-mail "
                    + "não se inscreve duas vezes no mesmo processo —, e a curva de chegada não "
                    + "existe aqui. Para acompanhar um processo aberto, use "
                    + "GET /v1/recruitment/processes/{processId}/applications/summary.")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping("/summary")
    public ApplicationHistorySummaryResponse summary(
            @ParameterObject CandidateApplicationFilter filter,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.summarizeHistory(filter);
    }
}
