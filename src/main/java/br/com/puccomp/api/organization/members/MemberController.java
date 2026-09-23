package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.organization.members.history.MemberHistoryResponse;
import br.com.puccomp.api.organization.members.summary.MemberSummaryResponse;
import br.com.puccomp.api.organization.members.summary.MemberSummaryService;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Membros")
@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService service;

    @Operation(summary = "Lista todos os membros paginados",
            description = """
                    Os filtros são combináveis por AND: department_id, role_id, course_id, status,
                    standing, has_role, has_department e q. UUID bem formado sem correspondência
                    devolve página vazia; valor malformado é 400. departmentId é alias depreciado
                    de department_id.

                    q casa nome e e-mail, sem acento e sem diferenciar maiúsculas — "joao" encontra
                    "João". Termo com menos de dois caracteres não filtra nada, em vez de varrer a
                    tabela; % e _ digitados valem como texto, não como curinga.

                    email é nulo em membro sem conta associada. joined_at é a primeira ativação
                    conhecida, e nulo significa que o membro já estava na EJ quando o rastreamento
                    começou — nunca que entrou agora.""")
    @ApiResponse(responseCode = "400", description = "Filtro inválido ou combinação contraditória",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "include_deleted=true sem members:write",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:read') and (#filter.includeDeleted() != true or hasAuthority('members:write'))")
    @GetMapping
    public Page<MemberResponse> getAll(
            @ParameterObject MemberFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"name", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.findAll(filter, pageable);
    }

    @Operation(summary = "Composição atual do quadro e contexto da estrutura da EJ",
            description = """
                    Irmão da listagem: aceita os mesmos filtros, inclusive q, com a mesma
                    normalização e as mesmas rejeições, e agrega todo o conjunto filtrado — page,
                    size e sort não têm efeito.

                    total, active_headcount e as distribuições descrevem a população filtrada. Já
                    organization_context descreve a EJ inteira, e nenhum filtro de membros o
                    restringe: um cargo com cinco vagas e cinco ocupantes não passa a ter quatro
                    vagas abertas porque só um ocupante corresponde ao curso filtrado.

                    seats e unfilled_roles exigem também roles:read; empty_departments exige também
                    departments:read. Sem a permissão adicional o bloco vem null — o que significa
                    ausência de permissão, não EJ vazia. As listas autorizadas nunca são nulas.""")
    @ApiResponse(responseCode = "400", description = "Filtro inválido ou combinação contraditória",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@PreAuthorize("hasAuthority('members:read') and (#filter.includeDeleted() != true or hasAuthority('members:write'))")
    @GetMapping("/summary")
    public MemberSummaryResponse summary(@ParameterObject MemberFilter filter,
                                         @Parameter(description = "Categorias identificadas por "
                                                 + "distribuição de recurso, de 1 a 20. Sem ele a "
                                                 + "distribuição vem inteira", example = "6")
                                         @RequestParam(name = "slice_limit", required = false)
                                         Integer sliceLimit,
                                         @Parameter(description = "Meses civis completos da janela "
                                                 + "de turnover, de 1 a 24", example = "12")
                                         @RequestParam(name = "turnover_months", defaultValue = "12")
                                         int turnoverMonths,
                                         Authentication authentication,
                                         HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.summarize(filter, new MemberSummaryService.ContextAccess(
                        has(authentication, "roles:read"), has(authentication, "departments:read")),
                new MemberSummaryService.SliceLimit(sliceLimit), turnoverMonths);
    }

    @Operation(summary = "Relatório temporal do quadro: entradas, saídas, retenção e permanência",
            description = """
                    Descreve a EJ inteira numa janela de meses civis completos, no fuso
                    America/Sao_Paulo. Exige members:read. Resposta sem cache.

                    from e to usam o formato AAAA-MM e vêm ambos ou nenhum. A janela é
                    [início de from, início de to): to é exclusivo, então ele pode ser o primeiro
                    mês ainda não concluído. O padrão é o último mês civil completo. Aceita de 1 a
                    24 meses; formato inválido, limite invertido ou igual, apenas um dos dois, e
                    janela que alcance o mês corrente pela metade ou o futuro são 400.

                    NÃO aceita os filtros de estado atual de /members/summary (status, role_id,
                    department_id, course_id, standing, has_role, has_department e o alias
                    departmentId): enviá-los é 400. Filtrar os ativos de hoje antes de contar as
                    saídas de meses passados falsearia turnover e retenção.

                    O período anterior tem a mesma quantidade de meses civis e termina onde este
                    começa; as duas janelas são avaliadas de forma independente, então uma janela
                    anterior sem cobertura implica apenas previous: null.

                    Tudo vem do histórico de vínculos, nunca do status atual. Janela que começa
                    antes de coverage.tracked_since tem métricas agregadas nulas, e mês não
                    integralmente coberto tem value nulo nas séries — desconhecido não é zero.""")
    @ApiResponse(responseCode = "400", description = "Janela inválida ou filtro de estado atual enviado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:read')")
    @GetMapping("/history")
    public MemberHistoryResponse history(
            @Parameter(description = "Primeiro mês da janela, inclusive", example = "2026-08")
            @RequestParam(required = false) String from,
            @Parameter(description = "Mês que fecha a janela, exclusive", example = "2026-09")
            @RequestParam(required = false) String to,
            @Parameter(hidden = true) @RequestParam Map<String, String> allParams,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.history(from, to, allParams.keySet());
    }

    @Operation(summary = "Busca membro por ID")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:read')")
    @GetMapping("/{id}")
    public MemberResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @Operation(summary = "Define o estado do vínculo",
            description = """
                    ALUMNUS encerra o ciclo ativo: a pessoa mantém leitura da EJ e perde a escrita.
                    ACTIVE devolve o quadro ativo, com o acesso que o cargo concede.

                    Idempotente: definir o estado que já vale não duplica a saída no histórico nem
                    move a data dela. Membro que saiu da EJ é 404 aqui — ele não existe mais para
                    a API.""")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:write')")
    @PutMapping("/{id}/status")
    public MemberResponse changeStatus(@PathVariable UUID id,
                                       @RequestBody @Valid MemberStatusRequest request) {
        return service.changeStatus(id, request.value());
    }

    @Operation(summary = "Remove um membro da EJ",
            description = """
                    A pessoa perde todo o acesso e some da listagem, do resumo e das distribuições.
                    Um GET do mesmo id passa a devolver 404.

                    A saída continua contando no relatório histórico: o intervalo ativo é encerrado
                    na remoção, senão quem saiu seguiria pesando no turnover para sempre.

                    Para ver quem saiu, use include_deleted=true na listagem; para desfazer, restore.""")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('members:write')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Devolve à EJ um membro removido",
            description = "O vínculo volta no estado em que saiu — reativação, nunca uma segunda "
                    + "admissão. Restaurar quem não foi removido não faz nada.")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:write')")
    @PostMapping("/{id}/restore")
    public MemberResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }

    @Operation(summary = "Define o cargo e a diretoria do membro; cargo com diretoria impõe a sua")
    @ApiResponse(responseCode = "404", description = "Membro, cargo ou departamento não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "O cargo pertence a outra diretoria",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:write')")
    @PutMapping("/{id}/assignment")
    public MemberResponse assign(@PathVariable UUID id, @RequestBody @Valid MemberAssignmentRequest request) {
        return service.assign(id, request);
    }

    private static boolean has(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}
