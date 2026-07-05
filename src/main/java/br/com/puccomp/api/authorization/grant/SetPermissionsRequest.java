package br.com.puccomp.api.authorization.grant;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetPermissionsRequest(
        @NotNull List<String> permissions
) { }
