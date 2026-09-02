package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.tenant.OrganizationView;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.organization.MemberDirectory.Membership;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
class AuthService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MemberDirectory memberDirectory;
    private final TenantRepository tenants;

    LoginResponse login(LoginRequest request) {
        var account = accounts.findByEmailIgnoreCase(request.email().trim())
                .filter(Account::isActive)
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash()))
            throw new UnauthorizedException("Credenciais inválidas");

        Membership membership = selectMembership(account, request.organizationId());

        return new LoginResponse(
                jwtService.generateAccessToken(account, membership.tenantId(), membership.memberId(),
                        membership.standing()),
                "Bearer",
                jwtService.accessTokenTtlSeconds()
        );
    }

    MeResponse me(AuthPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        var profile = principal.memberId() == null ? null
                : memberDirectory.findProfile(principal.memberId()).orElse(null);
        var organization = tenants.findById(principal.tenantId()).map(OrganizationView::from).orElse(null);
        return MeResponse.from(principal, organization, profile, permissionCodes(authorities));
    }

    /** O standing entra como {@code ROLE_*}; só os códigos de permissão são vocabulário público. */
    private static List<String> permissionCodes(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .sorted()
                .toList();
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
            throw new ConflictException("Conta vinculada a várias EJs; informe o organization_id no login");
        return memberships.getFirst();
    }
}
