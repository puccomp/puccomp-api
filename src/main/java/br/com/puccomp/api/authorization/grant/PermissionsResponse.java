package br.com.puccomp.api.authorization.grant;

import java.util.List;

public record PermissionsResponse(List<String> permissions) { }
