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
            description = "Os filtros são combináveis por AND: department_id, role_id, course_id, "
                    + "status, standing, has_role e has_department. UUID bem formado sem "
                    + "correspondência devolve página vazia; valor malformado é 400. "
                    + "departmentId é alias depreciado de department_id.")
    @ApiResponse(responseCode = "400", description = "Filtro inválido ou combinação contraditória",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:read')")
    @GetMapping
    public Page<MemberResponse> getAll(
            @ParameterObject MemberFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"name", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.findAll(filter, pageable);
    }

    @Operation(summary = "Composição atual do quadro e contexto da estrutura da EJ",
            description = """
                    Irmão da listagem: aceita os mesmos filtros, com a mesma normalização e as mesmas
                    rejeições, e agrega todo o conjunto filtrado — page, size e sort não têm efeito.

                    total, active_headcount e as distribuições descrevem a população filtrada. Já
                    organization_context descreve a EJ inteira, e nenhum filtro de membros o
                    restringe: um cargo com cinco vagas e cinco ocupantes não passa a ter quatro
                    vagas abertas porque só um ocupante corresponde ao curso filtrado.

                    seats e unfilled_roles exigem também roles:read; empty_departments exige também
                    departments:read. Sem a permissão adicional o bloco vem null — o que significa
                    ausência de permissão, não EJ vazia. As listas autorizadas nunca são nulas.""")
    @ApiResponse(responseCode = "400", description = "Filtro inválido ou combinação contraditória",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:read')")
    @GetMapping("/summary")
    public MemberSummaryResponse summary(@ParameterObject MemberFilter filter,
                                         Authentication authentication,
                                         HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.summarize(filter, new MemberSummaryService.ContextAccess(
                has(authentication, "roles:read"), has(authentication, "departments:read")));
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

    @Operation(summary = "Aposenta um membro: vira alumni, com acesso somente leitura à EJ")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:write')")
    @PostMapping("/{id}/retire")
    public MemberResponse retire(@PathVariable UUID id) {
        return service.retire(id);
    }

    @Operation(summary = "Reativa um membro aposentado, devolvendo o vínculo ativo")
    @ApiResponse(responseCode = "404", description = "Membro não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:write')")
    @PostMapping("/{id}/reactivate")
    public MemberResponse reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
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
