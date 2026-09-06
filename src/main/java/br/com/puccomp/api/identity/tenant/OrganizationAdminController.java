package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import br.com.puccomp.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin")
@SecurityRequirement(name = OpenApiConfig.PLATFORM_KEY_SCHEME)
@RestController
@RequestMapping("/v1/admin/organizations")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private final TenantProvisioningService service;

    @Operation(summary = "Provisiona uma EJ com catálogo de cursos e convite do primeiro OWNER")
    @ApiResponse(responseCode = "401", description = "Chave administrativa ausente ou inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Slug já existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ProvisionedOrganizationResponse provision(@RequestBody @Valid ProvisionOrganizationRequest request) {
        return service.provision(request);
    }
}
