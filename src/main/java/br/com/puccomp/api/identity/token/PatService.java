package br.com.puccomp.api.identity.token;

import br.com.puccomp.api.authorization.PermissionResolver;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.exception.ValidationException;
import br.com.puccomp.api.shared.token.TokenSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatService {

    private static final String PREFIX = "pat_";
    private static final int PREFIX_DISPLAY_LENGTH = 12;

    /**
     * Resolução de {@code lastUsedAt}. Quem audita quer saber se o token ainda circula, não
     * cronometrá-lo — e a diferença entre gravar sempre e gravar a cada janela é um UPDATE por
     * chamada, num caminho que agente percorre muito mais que tela.
     */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

    private final PatRepository repository;
    private final AccountRepository accounts;
    private final MemberDirectory memberDirectory;
    private final PermissionResolver permissions;

    @Transactional
    PatCreatedResponse create(AuthPrincipal owner, CreatePatRequest request) {
        String rawToken = PREFIX + TokenSecrets.randomSecret();
        PersonalAccessToken pat = PersonalAccessToken.builder()
                .tenantId(owner.tenantId())
                .accountId(owner.accountId())
                .name(request.name().trim())
                .tokenHash(TokenSecrets.sha256Hex(rawToken))
                .tokenPrefix(rawToken.substring(0, PREFIX_DISPLAY_LENGTH))
                .scopes(validatedScopes(request.scopes()))
                .expiresAt(request.expiresAt())
                .build();
        repository.save(pat);
        return new PatCreatedResponse(pat.getId(), pat.getName(), rawToken, pat.getExpiresAt());
    }

    List<PatResponse> list(AuthPrincipal owner) {
        return repository.findByAccountIdAndRevokedAtIsNull(owner.accountId()).stream()
                .map(PatResponse::from)
                .toList();
    }

    @Transactional
    void revoke(AuthPrincipal owner, UUID id) {
        PersonalAccessToken pat = repository.findByIdAndAccountId(id, owner.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Token não encontrado"));
        pat.revoke();
    }

    @Transactional
    public Optional<AuthPrincipal> authenticate(String rawToken) {
        return repository.findByTokenHash(TokenSecrets.sha256Hex(rawToken))
                .filter(candidate -> candidate.isUsable())
                .flatMap(pat -> accounts.findById(pat.getAccountId())
                        .filter(candidate -> candidate.isActive())
                        .flatMap(account -> memberDirectory.findMembership(pat.getAccountId(), pat.getTenantId())
                                .map(membership -> {
                                    markUsed(pat);
                                    return new AuthPrincipal(account.getId(), account.getEmail(),
                                            membership.tenantId(), membership.memberId(), membership.standing(),
                                            parseScopes(pat.getScopes()));
                                })));
    }

    /**
     * Escopo que não corresponde a nenhuma permissão não dá erro depois: o filtro intersecta escopo
     * com permissão efetiva, e um código digitado errado some na interseção, deixando um token sem
     * poder nenhum. A criação é a única hora em que ainda dá para avisar quem errou.
     */
    private String validatedScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) return null;

        Set<String> requested = scopes.stream()
                .map(scope -> scope == null ? "" : scope.trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> known = permissions.catalog();
        List<String> unknown = requested.stream()
                .filter(scope -> !known.contains(scope))
                .map(scope -> scope.isEmpty() ? "(vazio)" : scope)
                .toList();
        if (!unknown.isEmpty())
            throw new ValidationException("Escopo desconhecido: " + String.join(", ", unknown)
                    + ". Os escopos aceitos estão em GET /v1/auth/pat/scopes");

        return String.join(",", requested);
    }

    /** O catálogo que a criação aceita, para quem monta a requisição saber o que existe. */
    Set<String> availableScopes() {
        return permissions.catalog();
    }

    private static void markUsed(PersonalAccessToken pat) {
        Instant now = Instant.now();
        Instant last = pat.getLastUsedAt();
        if (last == null || last.isBefore(now.minus(LAST_USED_RESOLUTION)))
            pat.markUsed(now);
    }

    private static Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) return null;
        return Arrays.stream(scopes.split(","))
                .map(value -> value.trim()).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
