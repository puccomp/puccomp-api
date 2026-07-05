package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class AuthService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MemberDirectory memberDirectory;

    LoginResponse login(LoginRequest request) {
        var account = accounts.findByEmailIgnoreCase(request.email().trim())
                .filter(Account::isActive)
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash()))
            throw new UnauthorizedException("Credenciais inválidas");

        Membership membership = selectMembership(account, request.tenantId());

        return new LoginResponse(
                jwtService.generateAccessToken(account, membership.tenantId(), membership.memberId(),
                        membership.standing()),
                "Bearer",
                jwtService.accessTokenTtlSeconds()
        );
    }

    MeResponse me(AuthPrincipal principal) {
        var profile = principal.memberId() == null ? null
                : memberDirectory.findProfile(principal.memberId()).orElse(null);
        return MeResponse.from(principal, profile);
    }

    private Membership selectMembership(Account account, java.util.UUID requestedTenantId) {
        List<Membership> memberships = memberDirectory.findMembershipsByAccount(account.getId());

        if (requestedTenantId != null)
            return memberships.stream()
                    .filter(m -> m.tenantId().equals(requestedTenantId))
                    .findFirst()
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (memberships.isEmpty())
            throw new UnauthorizedException("Conta sem vínculo com nenhuma EJ");
        if (memberships.size() > 1)
            throw new ConflictException("Conta vinculada a várias EJs; informe o tenantId no login");
        return memberships.getFirst();
    }
}
