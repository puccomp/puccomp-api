package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Ferramentas MCP dos cargos. Ficam aqui pelo motivo descrito em {@code MemberTools}. */
@Component
@RequiredArgsConstructor
public class RoleTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final RoleService service;
    private final ObjectMapper json;

    @McpTool(name = "roles_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os cargos da EJ, com a diretoria de cada um e o número de vagas. \
                    Exige a permissão roles:read.

                    É por aqui que se obtém o role_id que members_list aceita como filtro.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('roles:read')")
    public String list(
            @McpToolParam(required = false,
                    description = "Traz só os cargos desta diretoria") UUID department_id,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return json.writeValueAsString(ToolPage.of(service.findAll(department_id, PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                BY_NAME))));
    }

    @McpTool(name = "roles_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca um cargo da EJ pelo id. Exige a permissão roles:read.")
    @PreAuthorize("hasAuthority('roles:read')")
    public String get(
            @McpToolParam(description = "Id do cargo, como devolvido por roles_list") UUID id) {
        return json.writeValueAsString(service.findById(id));
    }
}
