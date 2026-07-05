package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.shared.reference.Standing;

import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(UUID accountId, String email, UUID tenantId, UUID memberId, Standing standing,
                            Set<String> scopes) { }
