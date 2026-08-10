package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Membros")
@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService service;

    @Operation(summary = "Lista todos os membros paginados")
    @Parameter(name = "sort", description = "Padrão: name,asc e id,asc", example = "name,asc", in = ParameterIn.QUERY)
    @PreAuthorize("hasAuthority('members:read')")
    @GetMapping
    public Page<MemberResponse> getAll(@PageableDefault(size = 20,sort = {"name", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findAll(pageable);
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
}
