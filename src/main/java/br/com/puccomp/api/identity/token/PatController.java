package br.com.puccomp.api.identity.token;

import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tokens de acesso (PAT)")
@RestController
@RequestMapping("/v1/auth/pat")
@RequiredArgsConstructor
public class PatController {

    private final PatService service;

    @Operation(summary = "Cria um PAT; o valor do token é retornado uma única vez")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public PatCreatedResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestBody @Valid CreatePatRequest request) {
        return service.create(principal, request);
    }

    @Operation(summary = "Lista os PATs ativos da conta autenticada (sem o valor do token)")
    @GetMapping
    public List<PatResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal);
    }

    @Operation(summary = "Revoga um PAT da conta autenticada")
    @ApiResponse(responseCode = "404", description = "Token não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void revoke(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        service.revoke(principal, id);
    }
}
