package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.applications.CandidateApplicationFilter;
import br.com.puccomp.api.recruitment.applications.CandidateApplicationService;
import br.com.puccomp.api.recruitment.processes.SelectionProcessService;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** As ferramentas MCP de recrutamento. Convenções da superfície em {@code shared.mcp}. */
@Component
@RequiredArgsConstructor
public class RecruitmentTools {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final SelectionProcessService processes;
    private final CandidateApplicationService applications;
    private final ObjectMapper json;

    @McpTool(name = "recruitment_processes_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os processos seletivos da EJ, do mais recente para o mais antigo, com a \
                    contagem de inscrições de cada um. Exige a permissão recruitment:read.

                    O filtro de status casa com o status efetivo, e não com o que está gravado: \
                    OPEN traz só quem ainda está dentro do prazo, e IN_REVIEW inclui quem continua \
                    gravado como OPEN mas já venceu.

                    É por aqui que se obtém o process_id que as demais ferramentas de recrutamento \
                    aceitam.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public String processesList(
            @McpToolParam(required = false,
                    description = "Status efetivo; sem ele, todos entram") SelectionProcessStatus status,
            @McpToolParam(required = false,
                    description = "Busca no título, ignorando acento e caixa; menos de 2 caracteres "
                            + "é desconsiderado") String q,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return json.writeValueAsString(ToolPage.of(
                processes.findAll(status, q, ToolPage.request(page, size, NEWEST_FIRST))));
    }

    @McpTool(name = "recruitment_processes_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Busca um processo seletivo pelo id, com as etapas, o prazo e a configuração do \
                    formulário. Exige a permissão recruitment:read.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public String processesGet(
            @McpToolParam(description = "Id do processo, como devolvido por "
                    + "recruitment_processes_list") UUID process_id) {
        return json.writeValueAsString(processes.findById(process_id));
    }

    @McpTool(name = "recruitment_applications_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista as inscrições da EJ, da mais recente para a mais antiga. \
                    Exige a permissão recruitment:read.

                    Sem process_id, percorre o histórico inteiro; com ele, recorta um processo. \
                    Todos os filtros são opcionais e combinam por E.

                    Isto é uma busca filtrada, então process_id inexistente devolve total 0, e não \
                    erro. Para saber se um processo existe, use recruitment_processes_get.

                    Cada linha já responde reincidência sem varrer páginas: applications_count \
                    maior que 1 é quem voltou, e first_applied_at diz desde quando. Os dois olham \
                    para a EJ inteira, e nenhum filtro desta consulta os restringe.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public String applicationsList(
            @McpToolParam(required = false,
                    description = "Recorta um processo; sem ele, o histórico inteiro") UUID process_id,
            @McpToolParam(required = false,
                    description = "Busca por nome, e-mail e caixa, ignorando acento") String q,
            @McpToolParam(required = false, description = "Curso do candidato") UUID course_id,
            @McpToolParam(required = false, description = "Período mínimo, inclusive") Short min_term,
            @McpToolParam(required = false, description = "Período máximo, inclusive") Short max_term,
            @McpToolParam(required = false,
                    description = "true traz só quem anexou currículo; false só quem não anexou") Boolean has_cv,
            @McpToolParam(required = false,
                    description = "true traz só quem enviou ao menos um link") Boolean has_links,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas a partir deste instante ISO-8601") Instant from,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas até este instante ISO-8601") Instant to,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        var filter = filtro(process_id, q, course_id, min_term, max_term, has_cv, has_links, from, to);
        return json.writeValueAsString(ToolPage.of(applications.searchAcrossProcesses(
                filter, ToolPage.request(page, size, NEWEST_FIRST))));
    }

    @McpTool(name = "recruitment_process_funnel",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Retrato agregado das inscrições de UM processo seletivo. \
                    Exige a permissão recruitment:read.

                    Responde de uma vez o que a listagem só responderia paginando tudo: \
                    distribuição por curso e por período, quantos anexaram currículo, e a curva de \
                    chegada por dia com o pico destacado.

                    É a visão de dentro do prazo — a curva diária, o pico e last_day_share só \
                    existem aqui. Para comparar processos entre si e contar pessoas distintas, use \
                    recruitment_applications_summary.

                    Processo sem inscrições devolve contagens zero e listas vazias; processo que \
                    não existe devolve erro. São coisas diferentes, e a resposta distingue as duas.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public String processFunnel(
            @McpToolParam(description = "Id do processo, como devolvido por "
                    + "recruitment_processes_list") UUID process_id,
            @McpToolParam(required = false,
                    description = "Busca por nome, e-mail e caixa, ignorando acento") String q,
            @McpToolParam(required = false, description = "Curso do candidato") UUID course_id,
            @McpToolParam(required = false, description = "Período mínimo, inclusive") Short min_term,
            @McpToolParam(required = false, description = "Período máximo, inclusive") Short max_term,
            @McpToolParam(required = false, description = "true traz só quem anexou currículo") Boolean has_cv,
            @McpToolParam(required = false,
                    description = "true traz só quem enviou ao menos um link") Boolean has_links,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas a partir deste instante ISO-8601") Instant from,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas até este instante ISO-8601") Instant to) {

        return json.writeValueAsString(applications.summarize(process_id,
                filtro(null, q, course_id, min_term, max_term, has_cv, has_links, from, to)));
    }

    @McpTool(name = "recruitment_applications_summary",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Retrato agregado das inscrições da EJ inteira. \
                    Exige a permissão recruitment:read.

                    Responde o que nenhum processo isolado responde: quantas pessoas diferentes já \
                    passaram pelo funil, quantas voltaram, como o volume evoluiu entre processos, \
                    em que meses as inscrições chegam e que cursos do catálogo a EJ nunca alcançou.

                    Atenção à unidade: total e as distribuições estão em inscrições e fecham entre \
                    si; candidates está em pessoas e não fecha com nenhuma delas — quem se \
                    inscreveu em três processos é três inscrições e uma pessoa.

                    process_id recorta um processo, mas não substitui recruitment_process_funnel: \
                    assim filtrado, candidates.distinct iguala total e returning é sempre zero, e a \
                    curva de chegada não existe aqui.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public String applicationsSummary(
            @McpToolParam(required = false, description = "Recorta um processo") UUID process_id,
            @McpToolParam(required = false,
                    description = "Busca por nome, e-mail e caixa, ignorando acento") String q,
            @McpToolParam(required = false, description = "Curso do candidato") UUID course_id,
            @McpToolParam(required = false, description = "Período mínimo, inclusive") Short min_term,
            @McpToolParam(required = false, description = "Período máximo, inclusive") Short max_term,
            @McpToolParam(required = false, description = "true traz só quem anexou currículo") Boolean has_cv,
            @McpToolParam(required = false,
                    description = "true traz só quem enviou ao menos um link") Boolean has_links,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas a partir deste instante ISO-8601") Instant from,
            @McpToolParam(required = false,
                    description = "Inscrições enviadas até este instante ISO-8601") Instant to) {

        return json.writeValueAsString(applications.summarizeHistory(
                filtro(process_id, q, course_id, min_term, max_term, has_cv, has_links, from, to)));
    }

    private static CandidateApplicationFilter filtro(UUID processId, String q, UUID courseId,
                                                     Short minTerm, Short maxTerm, Boolean hasCv,
                                                     Boolean hasLinks, Instant from, Instant to) {
        return new CandidateApplicationFilter(q, processId, courseId, minTerm, maxTerm,
                hasCv, hasLinks, from, to);
    }
}
