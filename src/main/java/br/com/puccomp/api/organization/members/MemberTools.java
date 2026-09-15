package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.members.summary.MemberSummaryResponse;
import br.com.puccomp.api.organization.members.summary.MemberSummaryService;
import br.com.puccomp.api.shared.mcp.ToolPage;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
                    se vale pedir a próxima página, em vez de supor.

                    Para contagens e distribuições do quadro inteiro, prefira members_summary: ele \
                    responde de uma vez o que esta listagem só responderia paginando tudo.""")
    @PreAuthorize("hasAuthority('members:read')")
    public ToolPage<MemberResponse> list(
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

        return ToolPage.of(service.findAll(filtro(status, standing, departmentId, roleId, courseId),
                PageRequest.of(pagina(page), tamanho(size), BY_NAME)));
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

    @McpTool(name = "members_summary",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Composição atual do quadro: total, ativos e as distribuições por cargo, \
                    diretoria, curso e situação. Exige a permissão members:read.

                    Aceita os mesmos filtros de members_list e agrega o conjunto filtrado inteiro, \
                    sem paginar. Já o bloco organization_context descreve a EJ inteira e nenhum \
                    filtro o restringe — um cargo com cinco vagas e cinco ocupantes não passa a ter \
                    quatro vagas abertas porque só um ocupante corresponde ao curso filtrado.

                    Dentro dele, seats e unfilled_roles exigem também roles:read, e \
                    empty_departments exige também departments:read. Sem a permissão adicional o \
                    bloco vem nulo, o que significa ausência de permissão e não EJ vazia.""")
    @PreAuthorize("hasAuthority('members:read')")
    public MemberSummaryResponse summary(
            @McpToolParam(required = false, description = "Situação do vínculo") MemberStatus status,
            @McpToolParam(required = false, description = "Tipo de vínculo com a EJ") Standing standing,
            @McpToolParam(required = false, description = "Diretoria atual do membro") UUID departmentId,
            @McpToolParam(required = false, description = "Cargo atual do membro") UUID roleId,
            @McpToolParam(required = false, description = "Curso do membro") UUID courseId) {

        return service.summarize(filtro(status, standing, departmentId, roleId, courseId),
                new MemberSummaryService.ContextAccess(pode("roles:read"), pode("departments:read")));
    }

    private static MemberFilter filtro(MemberStatus status, Standing standing,
                                       UUID departmentId, UUID roleId, UUID courseId) {
        return new MemberFilter(departmentId, null, roleId, courseId, status, standing, null, null);
    }

    private static int pagina(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int tamanho(Integer size) {
        return size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * O contexto da estrutura depende de permissões além de members:read, e o controller as lê da
     * {@code Authentication}. Aqui vale o mesmo: em {@code SYNC} a ferramenta roda na thread da
     * requisição, então o {@code SecurityContextHolder} é o mesmo que o {@code @PreAuthorize} usou.
     */
    private static boolean pode(String permissao) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permissao.equals(authority.getAuthority()));
    }
}
