package br.com.puccomp.api.identity.security;

import br.com.puccomp.api.authorization.PermissionResolver;
import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.identity.token.JwtService;
import br.com.puccomp.api.identity.token.PatService;
import br.com.puccomp.api.organization.MemberDirectory;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String PAT_PREFIX = "pat_";

    private final JwtService jwtService;
    private final PatService patService;
    private final PermissionResolver permissionResolver;
    private final MemberDirectory memberDirectory;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = bearerToken(request);
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null)
                authenticate(token, request);

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        Optional<AuthPrincipal> principal = token.startsWith(PAT_PREFIX)
                ? patService.authenticate(token)
                : authenticateJwt(token);
        principal.ifPresent(p -> applyAuthentication(p, request));
    }

    private Optional<AuthPrincipal> authenticateJwt(String token) {
        try {
            Jwt jwt = jwtService.decode(token);
            return Optional.of(new AuthPrincipal(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    UUID.fromString(jwt.getClaimAsString("tenant_id")),
                    UUID.fromString(jwt.getClaimAsString("member_id")),
                    Standing.valueOf(jwt.getClaimAsString("standing")),
                    null));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private void applyAuthentication(AuthPrincipal principal, HttpServletRequest request) {
        TenantContext.set(principal.tenantId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities(principal));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private List<GrantedAuthority> authorities(AuthPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.standing().name()));

        Set<String> permissions = effectivePermissions(principal);

        if (principal.scopes() != null)
            permissions = permissions.stream().filter(principal.scopes()::contains).collect(Collectors.toSet());

        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return authorities;
    }

    private Set<String> effectivePermissions(AuthPrincipal principal) {
        if (principal.memberId() != null && memberDirectory.isReadOnly(principal.memberId()))
            return permissionResolver.readOnlyAuthorities();
        if (isPlatformAdmin(principal.standing()))
            return permissionResolver.allAuthorities();
        return permissionResolver.resolveAuthorities(principal.memberId(), roleId(principal));
    }

    private boolean isPlatformAdmin(Standing standing) {
        return standing == Standing.OWNER || standing == Standing.ADMIN;
    }

    private UUID roleId(AuthPrincipal principal) {
        return principal.memberId() == null ? null
                : memberDirectory.findRoleId(principal.memberId()).orElse(null);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer "))
            return header.substring(7).trim();
        return null;
    }
}
