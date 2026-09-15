package br.com.puccomp.api.organization.courses;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Ferramentas MCP do catálogo de cursos. Ficam aqui pelo motivo descrito em {@code MemberTools}.
 *
 * <p>Sem {@code @PreAuthorize}: o catálogo já é legível por qualquer membro autenticado, como no
 * {@code CourseController}. Quem não tem token nenhum não chega até aqui — o endpoint {@code /mcp}
 * exige autenticação antes de qualquer ferramenta rodar.
 *
 * <p>Só uma ferramenta, e não o par listar/buscar dos outros módulos: o catálogo vem inteiro numa
 * chamada, então buscar por id devolveria ao agente algo que ele já tem. Ferramenta a mais é
 * contexto que ele paga em toda conversa.
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseService service;
    private final ObjectMapper json;

    @McpTool(name = "courses_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os cursos que a EJ aceita. É o catálogo inteiro, sem paginação.

                    É por aqui que se obtém o course_id que members_list e as ferramentas de \
                    recrutamento aceitam como filtro.

                    Devolve {items, total, page, pages}.""")
    public String list() {
        return json.writeValueAsString(ToolPage.of(service.findAll()));
    }

}
