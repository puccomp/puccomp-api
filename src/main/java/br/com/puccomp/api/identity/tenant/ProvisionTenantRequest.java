package br.com.puccomp.api.identity.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProvisionTenantRequest(
        @NotBlank String name,
        @NotBlank String slug,
        @NotBlank @Email String ownerEmail,
        @NotEmpty List<@NotBlank String> courses
) { }
