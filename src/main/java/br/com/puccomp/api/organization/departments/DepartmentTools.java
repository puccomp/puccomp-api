package br.com.puccomp.api.organization.departments;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Ferramentas MCP das diretorias. Ficam aqui pelo motivo descrito em {@code MemberTools}. */
@Component
@RequiredArgsConstructor
public class DepartmentTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final DepartmentService service;

    @McpTool(name = "departments_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista as diretorias da EJ. Exige a permissão departments:read.

                    É por aqui que se obtém o department_id que members_list e roles_list aceitam \
                    como filtro.""")
    @PreAuthorize("hasAuthority('departments:read')")
    public ToolPage<DepartmentResponse> list(
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return ToolPage.of(service.findAll(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                BY_NAME)));
    }

    @McpTool(name = "departments_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca uma diretoria da EJ pelo id. Exige a permissão departments:read.")
    @PreAuthorize("hasAuthority('departments:read')")
    public DepartmentResponse get(
            @McpToolParam(description = "Id da diretoria, como devolvido por departments_list") UUID id) {
        return service.findById(id);
    }
}
