package br.com.puccomp.api.recruitment.applications;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Ferramentas MCP das inscrições. Ficam aqui pelo motivo descrito em {@code MemberTools}.
 *
 * <p>São três e não quatro de propósito. A listagem por processo é a busca da EJ inteira com
 * {@code process_id} preenchido, então uma ferramenta cobre as duas; já os dois resumos respondem
 * perguntas diferentes — um enxerga a curva de chegada dentro do prazo, o outro enxerga pessoas
 * entre processos — e juntá-los só faria o agente receber campos nulos sem saber por quê.
 *
 * <p>O sublinhado nos parâmetros de ferramenta é deliberado, e o motivo está em {@code MemberTools}.
 */
@Component
@RequiredArgsConstructor
public class CandidateApplicationTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final CandidateApplicationService service;
    private final ObjectMapper json;

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
    public String list(
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
        return json.writeValueAsString(ToolPage.of(service.searchAcrossProcesses(filter, PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                NEWEST_FIRST))));
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
    public String funnel(
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

        return json.writeValueAsString(service.summarize(process_id,
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
    public String summary(
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

        return json.writeValueAsString(service.summarizeHistory(
                filtro(process_id, q, course_id, min_term, max_term, has_cv, has_links, from, to)));
    }

    private static CandidateApplicationFilter filtro(UUID processId, String q, UUID courseId,
                                                     Short minTerm, Short maxTerm, Boolean hasCv,
                                                     Boolean hasLinks, Instant from, Instant to) {
        return new CandidateApplicationFilter(q, processId, courseId, minTerm, maxTerm,
                hasCv, hasLinks, from, to);
    }
}
