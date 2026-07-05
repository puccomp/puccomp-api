package br.com.puccomp.api.authorization.grant;

import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Autorização")
@RestController
@RequestMapping("/v1/authz")
@PreAuthorize("hasAuthority('permissions:manage')")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService service;

    @Operation(summary = "Lista o catálogo de permissões disponíveis no sistema")
    @GetMapping("/permissions")
    public PermissionsResponse catalog() {
        return new PermissionsResponse(service.allAuthorities().stream().sorted().toList());
    }

    @Operation(summary = "Permissões atribuídas a um cargo da organização")
    @GetMapping("/roles/{roleId}/permissions")
    public PermissionsResponse getRolePermissions(@PathVariable UUID roleId) {
        return new PermissionsResponse(service.getRolePermissions(roleId));
    }

    @Operation(summary = "Define sobrescrevendo as permissões de um cargo da organização")
    @ApiResponse(responseCode = "400", description = "Código de permissão inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/roles/{roleId}/permissions")
    public PermissionsResponse setRolePermissions(@PathVariable UUID roleId,
                                                  @RequestBody @Valid SetPermissionsRequest request) {
        service.setRolePermissions(roleId, parse(request));
        return new PermissionsResponse(service.getRolePermissions(roleId));
    }

    @Operation(summary = "Permissões extras de um membro")
    @GetMapping("/members/{memberId}/permissions")
    public PermissionsResponse getMemberPermissions(@PathVariable UUID memberId) {
        return new PermissionsResponse(service.getMemberPermissions(memberId));
    }

    @Operation(summary = "Define sobrescrevendo as permissões extras de um membro")
    @ApiResponse(responseCode = "400", description = "Código de permissão inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/members/{memberId}/permissions")
    public PermissionsResponse setMemberPermissions(@PathVariable UUID memberId,
                                                    @RequestBody @Valid SetPermissionsRequest request) {
        service.setMemberPermissions(memberId, parse(request));
        return new PermissionsResponse(service.getMemberPermissions(memberId));
    }

    private Set<Permission> parse(SetPermissionsRequest request) {
        return request.permissions().stream()
                .map(code -> Permission.fromCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Permissão desconhecida: " + code)))
                .collect(Collectors.toSet());
    }
}
