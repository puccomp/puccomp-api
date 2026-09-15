package br.com.puccomp.api.organization.courses;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ferramentas MCP do catálogo de cursos. Ficam aqui pelo motivo descrito em {@code MemberTools}.
 *
 * <p>Sem {@code @PreAuthorize}: o catálogo já é legível por qualquer membro autenticado, como no
 * {@code CourseController}. Quem não tem token nenhum não chega até aqui — o endpoint {@code /mcp}
 * exige autenticação antes de qualquer ferramenta rodar.
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseService service;

    @McpTool(name = "courses_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os cursos que a EJ aceita. É o catálogo inteiro, sem paginação.

                    É por aqui que se obtém o course_id que members_list e as ferramentas de \
                    recrutamento aceitam como filtro.""")
    public ToolPage<CourseResponse> list() {
        return ToolPage.of(service.findAll());
    }

    @McpTool(name = "courses_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca um curso do catálogo da EJ pelo id.")
    public CourseResponse get(
            @McpToolParam(description = "Id do curso, como devolvido por courses_list") UUID id) {
        return service.findById(id);
    }
}
