package br.com.puccomp.api.identity.token;

import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import br.com.puccomp.api.shared.token.TokenSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
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

    private final PatRepository repository;
    private final AccountRepository accounts;
    private final MemberDirectory memberDirectory;

    @Transactional
    PatCreatedResponse create(AuthPrincipal owner, CreatePatRequest request) {
        String rawToken = PREFIX + TokenSecrets.randomSecret();
        PersonalAccessToken pat = PersonalAccessToken.builder()
                .tenantId(owner.tenantId())
                .accountId(owner.accountId())
                .name(request.name().trim())
                .tokenHash(TokenSecrets.sha256Hex(rawToken))
                .tokenPrefix(rawToken.substring(0, PREFIX_DISPLAY_LENGTH))
                .scopes(request.scopes() == null || request.scopes().isEmpty()
                        ? null : String.join(",", request.scopes()))
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
                .filter(PersonalAccessToken::isUsable)
                .flatMap(pat -> accounts.findById(pat.getAccountId())
                        .filter(Account::isActive)
                        .flatMap(account -> memberDirectory.findMembership(pat.getAccountId(), pat.getTenantId())
                                .map(membership -> {
                                    pat.markUsed(Instant.now());
                                    return new AuthPrincipal(account.getId(), account.getEmail(),
                                            membership.tenantId(), membership.memberId(), membership.standing(),
                                            parseScopes(pat.getScopes()));
                                })));
    }

    private static Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) return null;
        return Arrays.stream(scopes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
