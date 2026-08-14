package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin")
@RestController
@RequestMapping("/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantProvisioningService service;

    @Operation(summary = "Provisiona uma EJ com catalogo de cursos e convite do primeiro OWNER")
    @ApiResponse(responseCode = "401", description = "Chave administrativa ausente ou invalida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Slug ja existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ProvisionedTenantResponse provision(@RequestBody @Valid ProvisionTenantRequest request) {
        return service.provision(request);
    }
}
