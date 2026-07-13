package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.identity.account.LoginResponse;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Convites")
@RestController
@RequestMapping("/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService service;

    @Operation(summary = "Convida alguém para a EJ. Envia email com o link de aceitação")
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já possui conta",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('members:invite')")
    @PostMapping
    public InvitationResponse create(@AuthenticationPrincipal AuthPrincipal admin,
                                     @RequestBody @Valid CreateInvitationRequest request) {
        return service.create(admin, request);
    }

    @Operation(summary = "Lista os convites em aberto (não aceitos) da EJ")
    @PreAuthorize("hasAuthority('members:invite')")
    @GetMapping
    public Page<InvitationResponse> list(@AuthenticationPrincipal AuthPrincipal admin, Pageable pageable) {
        return service.listOutstanding(admin.tenantId(), pageable);
    }

    @Operation(summary = "Revoga um convite em aberto, invalidando o link de aceitação")
    @ApiResponse(responseCode = "404", description = "Convite não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Convite já foi aceito",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('members:invite')")
    @DeleteMapping("/{id}")
    public void revoke(@AuthenticationPrincipal AuthPrincipal admin, @PathVariable UUID id) {
        service.revoke(admin.tenantId(), id);
    }

    @Operation(summary = "Reenvia um convite: gera um novo token/link e estende a validade")
    @ApiResponse(responseCode = "404", description = "Convite não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Convite já aceito ou revogado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PreAuthorize("hasAuthority('members:invite')")
    @PostMapping("/{id}/resend")
    public InvitationResponse resend(@AuthenticationPrincipal AuthPrincipal admin, @PathVariable UUID id) {
        return service.resend(admin.tenantId(), id);
    }

    @Operation(summary = "Prévia pública do convite: nome da EJ e cursos disponíveis para preencher o aceite")
    @ApiResponse(responseCode = "400", description = "Convite inválido ou expirado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/accept")
    public InvitationPreviewResponse preview(@RequestParam String token) {
        return service.preview(token);
    }

    @Operation(summary = "Aceita um convite: define senha + perfil, cria a conta e já autentica")
    @ApiResponse(responseCode = "400", description = "Convite inválido ou expirado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/accept")
    public LoginResponse accept(@RequestBody @Valid AcceptInvitationRequest request) {
        return service.accept(request);
    }
}
