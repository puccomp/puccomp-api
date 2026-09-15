package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * As ferramentas MCP do quadro. Moram junto do controller, e não num módulo {@code mcp} central,
 * porque {@link MemberService} é package-private: um módulo de fora teria de alargar a visibilidade
 * de meio codebase para alcançá-lo. Como aqui só entram anotações do Spring AI — biblioteca, não
 * módulo — nenhuma dependência nova entre módulos nasce disto.
 *
 * <p>Tenant e permissão não são tratados aqui de propósito: o servidor é {@code STATELESS} e
 * {@code SYNC}, então a ferramenta roda na mesma thread da requisição, onde o
 * {@code BearerAuthenticationFilter} já deixou o {@code TenantContext} e as authorities prontos.
 *
 * <p>A descrição de cada ferramenta nomeia a permissão exigida porque a recusa do
 * {@code @PreAuthorize} chega ao agente como um "Access Denied" seco, que não diz o que faltou.
 * Ver ADR 0006.
 */
@Component
@RequiredArgsConstructor
public class MemberTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort BY_NAME = Sort.by("name", "id");

    private final MemberService service;

    @McpTool(name = "members_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os membros da Empresa Júnior. Exige a permissão members:read.

                    Todos os filtros são opcionais e combinam por E; sem nenhum, devolve o quadro \
                    inteiro em ordem alfabética. Os filtros por id esperam o id devolvido pela \
                    ferramenta do cadastro correspondente, não o nome. Consulte 'total' para saber \
                    se vale pedir a próxima página, em vez de supor.""")
    @PreAuthorize("hasAuthority('members:read')")
    public MemberList list(
            @McpToolParam(required = false,
                    description = "Situação do vínculo; sem ele, todas entram") MemberStatus status,
            @McpToolParam(required = false,
                    description = "Tipo de vínculo com a EJ; sem ele, todos entram") Standing standing,
            @McpToolParam(required = false, description = "Diretoria atual do membro") UUID departmentId,
            @McpToolParam(required = false, description = "Cargo atual do membro") UUID roleId,
            @McpToolParam(required = false, description = "Curso do membro") UUID courseId,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        var filter = new MemberFilter(departmentId, null, roleId, courseId, status, standing, null, null);
        Page<MemberResponse> found = service.findAll(filter, PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE),
                BY_NAME));

        return new MemberList(found.getContent(), found.getTotalElements(),
                found.getNumber(), found.getTotalPages());
    }

    @McpTool(name = "members_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Busca um membro da EJ pelo id, com o curso, o cargo e a diretoria atuais. \
                    Exige a permissão members:read.""")
    @PreAuthorize("hasAuthority('members:read')")
    public MemberResponse get(
            @McpToolParam(description = "Id do membro, como devolvido por members_list") UUID id) {
        return service.findById(id);
    }

    /**
     * O envelope de {@code Page} do Spring Data — {@code pageable}, {@code sort}, {@code first},
     * {@code numberOfElements} — é ruído que o agente paga em contexto a cada chamada. Aqui fica só
     * o que ele usa para decidir se pede mais.
     */
    public record MemberList(List<MemberResponse> members, long total, int page, int pages) { }
}
