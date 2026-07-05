package br.com.puccomp.api.identity.token;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatResponse(
        UUID id,
        String name,
        String tokenPrefix,
        List<String> scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt
) {

    static PatResponse from(PersonalAccessToken pat) {
        List<String> scopes = (pat.getScopes() == null || pat.getScopes().isBlank())
                ? List.of()
                : List.of(pat.getScopes().split(","));
        return new PatResponse(
                pat.getId(),
                pat.getName(),
                pat.getTokenPrefix(),
                scopes,
                pat.getExpiresAt(),
                pat.getLastUsedAt(),
                pat.getCreatedAt()
        );
    }
}
