package br.com.puccomp.api.recruitment.applications;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                    + "para a EJ inteira, e nenhum filtro desta consulta os restringe.")
    @PreAuthorize("hasAuthority('recruitment:read')")
    @GetMapping
    public Page<CandidateApplicationResponse> search(
            @ParameterObject CandidateApplicationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.searchAcrossProcesses(filter, pageable);
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
