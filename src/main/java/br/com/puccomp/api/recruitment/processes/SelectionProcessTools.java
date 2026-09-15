package br.com.puccomp.api.recruitment.processes;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Ferramentas MCP dos processos seletivos. Ficam aqui pelo motivo descrito em {@code MemberTools}. */
@Component
@RequiredArgsConstructor
public class SelectionProcessTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final SelectionProcessService service;

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
                    aceitam.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public ToolPage<SelectionProcessSummaryResponse> list(
            @McpToolParam(required = false,
                    description = "Status efetivo; sem ele, todos entram") SelectionProcessStatus status,
            @McpToolParam(required = false,
                    description = "Busca no título, ignorando acento e caixa; menos de 2 caracteres "
                            + "é desconsiderado") String q,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return ToolPage.of(service.findAll(status, q, PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                NEWEST_FIRST)));
    }

    @McpTool(name = "recruitment_processes_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Busca um processo seletivo pelo id, com as etapas, o prazo e a configuração do \
                    formulário. Exige a permissão recruitment:read.""")
    @PreAuthorize("hasAuthority('recruitment:read')")
    public SelectionProcessResponse get(
            @McpToolParam(description = "Id do processo, como devolvido por "
                    + "recruitment_processes_list") UUID processId) {
        return service.findById(processId);
    }
}
