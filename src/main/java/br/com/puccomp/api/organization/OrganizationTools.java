package br.com.puccomp.api.organization;

import br.com.puccomp.api.organization.courses.CourseService;
import br.com.puccomp.api.organization.departments.DepartmentService;
import br.com.puccomp.api.organization.members.MemberFilter;
import br.com.puccomp.api.organization.members.MemberService;
import br.com.puccomp.api.organization.members.MemberStatus;
import br.com.puccomp.api.organization.members.summary.MemberSummaryService;
import br.com.puccomp.api.organization.roles.RoleService;
import br.com.puccomp.api.shared.mcp.ToolPage;
import br.com.puccomp.api.shared.reference.Standing;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** As ferramentas MCP da estrutura da EJ. Convenções da superfície em {@code shared.mcp}. */
@Component
@RequiredArgsConstructor
public class OrganizationTools {

    private static final Sort BY_NAME = Sort.by("name", "id");

    private final MemberService members;
    private final RoleService roles;
    private final DepartmentService departments;
    private final CourseService courses;
    private final ObjectMapper json;

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
                    responde de uma vez o que esta listagem só responderia paginando tudo.

                    Cada membro traz nome, e-mail, situação, curso, cargo, diretoria, joined_at e \
                    left_at. joined_at nulo significa que a pessoa já estava na EJ quando o \
                    rastreamento começou, e não que entrou agora; left_at nulo é quem está ativo \
                    ou saiu antes do rastreamento — quem separa os dois casos é o status.

                    Quem saiu da EJ não aparece aqui de forma alguma: a remoção some do contrato.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('members:read')")
    public String membersList(
            @McpToolParam(required = false,
                    description = "Situação do vínculo; sem ele, todas entram") MemberStatus status,
            @McpToolParam(required = false,
                    description = "Tipo de vínculo com a EJ; sem ele, todos entram") Standing standing,
            @McpToolParam(required = false, description = "Diretoria atual do membro") UUID department_id,
            @McpToolParam(required = false, description = "Cargo atual do membro") UUID role_id,
            @McpToolParam(required = false, description = "Curso do membro") UUID course_id,
            @McpToolParam(required = false,
                    description = "Busca por nome ou e-mail, sem acento e sem diferenciar "
                            + "maiúsculas: 'joao' encontra 'João'. Termo com menos de dois "
                            + "caracteres não filtra nada") String q,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return json.writeValueAsString(ToolPage.of(members.findAll(
                filtro(status, standing, department_id, role_id, course_id, q),
                ToolPage.request(page, size, BY_NAME))));
    }

    @McpTool(name = "members_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Busca um membro da EJ pelo id, com o curso, o cargo e a diretoria atuais, \
                    mais o e-mail e as datas de entrada e de saída. Exige a permissão members:read.

                    Membro removido da EJ não é encontrado por aqui: ele deixou de existir para a \
                    API.""")
    @PreAuthorize("hasAuthority('members:read')")
    public String membersGet(
            @McpToolParam(description = "Id do membro, como devolvido por members_list") UUID id) {
        return json.writeValueAsString(members.findById(id));
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
                    bloco vem nulo, o que significa ausência de permissão e não EJ vazia.

                    tenure é o tempo de casa do recorte, em meses: quem está ativo conta até \
                    agora, quem saiu conta até a saída. Use a mediana, não a média — um fundador \
                    antigo puxa a média. unknown_start são os membros anteriores ao rastreamento, \
                    que ficam fora das duas medidas.

                    turnover são as saídas na janela de turnover_months sobre o quadro médio. \
                    CUIDADO ao filtrar: o recorte usa a atribuição de HOJE, então department_id \
                    descreve as saídas de quem hoje está nessa diretoria, e não as saídas que a \
                    diretoria teve — cargo e diretoria não têm histórico. Não relate esse número \
                    como turnover de uma diretoria.

                    Nas distribuições, key.kind diz o que a categoria é: ITEM é uma categoria \
                    real, ABSENT é quem não tem o vínculo, e OTHERS é a cauda agregada por \
                    slice_limit. Os dois últimos têm id nulo, e só o kind os distingue.""")
    @PreAuthorize("hasAuthority('members:read')")
    public String membersSummary(
            @McpToolParam(required = false, description = "Situação do vínculo") MemberStatus status,
            @McpToolParam(required = false, description = "Tipo de vínculo com a EJ") Standing standing,
            @McpToolParam(required = false, description = "Diretoria atual do membro") UUID department_id,
            @McpToolParam(required = false, description = "Cargo atual do membro") UUID role_id,
            @McpToolParam(required = false, description = "Curso do membro") UUID course_id,
            @McpToolParam(required = false, description = "Busca por nome ou e-mail") String q,
            @McpToolParam(required = false,
                    description = "Quantas categorias identificadas manter em cada distribuição "
                            + "por recurso, de 1 a 20; o resto vira uma categoria OTHERS. Sem ele "
                            + "a distribuição vem inteira, o que numa EJ grande é resposta longa "
                            + "à toa") Integer slice_limit,
            @McpToolParam(required = false,
                    description = "Meses da janela de turnover, de 1 a 24; o padrão é 12")
            Integer turnover_months) {

        return json.writeValueAsString(members.summarize(
                filtro(status, standing, department_id, role_id, course_id, q),
                new MemberSummaryService.ContextAccess(pode("roles:read"), pode("departments:read")),
                new MemberSummaryService.SliceLimit(slice_limit),
                turnover_months == null ? 12 : turnover_months));
    }

    @McpTool(name = "roles_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os cargos da EJ, com a diretoria de cada um e o número de vagas. \
                    Exige a permissão roles:read.

                    É por aqui que se obtém o role_id que members_list aceita como filtro.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('roles:read')")
    public String rolesList(
            @McpToolParam(required = false,
                    description = "Traz só os cargos desta diretoria") UUID department_id,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return json.writeValueAsString(ToolPage.of(
                roles.findAll(department_id, ToolPage.request(page, size, BY_NAME))));
    }

    @McpTool(name = "roles_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca um cargo da EJ pelo id. Exige a permissão roles:read.")
    @PreAuthorize("hasAuthority('roles:read')")
    public String rolesGet(
            @McpToolParam(description = "Id do cargo, como devolvido por roles_list") UUID id) {
        return json.writeValueAsString(roles.findById(id));
    }

    @McpTool(name = "departments_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista as diretorias da EJ. Exige a permissão departments:read.

                    É por aqui que se obtém o department_id que members_list e roles_list aceitam \
                    como filtro.

                    Devolve {items, total, page, pages}.""")
    @PreAuthorize("hasAuthority('departments:read')")
    public String departmentsList(
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return json.writeValueAsString(ToolPage.of(
                departments.findAll(ToolPage.request(page, size, BY_NAME))));
    }

    @McpTool(name = "departments_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca uma diretoria da EJ pelo id. Exige a permissão departments:read.")
    @PreAuthorize("hasAuthority('departments:read')")
    public String departmentsGet(
            @McpToolParam(description = "Id da diretoria, como devolvido por departments_list") UUID id) {
        return json.writeValueAsString(departments.findById(id));
    }

    // Sem @PreAuthorize: o catálogo é legível por qualquer membro autenticado, como no CourseController.
    @McpTool(name = "courses_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os cursos que a EJ aceita. É o catálogo inteiro, sem paginação.

                    É por aqui que se obtém o course_id que members_list e as ferramentas de \
                    recrutamento aceitam como filtro.

                    Devolve {items, total, page, pages}.""")
    public String coursesList() {
        return json.writeValueAsString(ToolPage.of(courses.findAll()));
    }

    /** include_deleted fica de fora: ele exige members:write, e a superfície MCP é de leitura. */
    private static MemberFilter filtro(MemberStatus status, Standing standing, UUID departmentId,
                                       UUID roleId, UUID courseId, String q) {
        return new MemberFilter(departmentId, null, roleId, courseId, status, standing, null, null,
                q, null);
    }

    /**
     * Em {@code SYNC} a ferramenta roda na thread da requisição, então este é o mesmo contexto que
     * o {@code @PreAuthorize} acabou de consultar.
     */
    private static boolean pode(String permissao) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permissao.equals(authority.getAuthority()));
    }
}
