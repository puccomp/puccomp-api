package br.com.puccomp.api.identity.security;

import br.com.puccomp.api.identity.tenant.Tenant;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.identity.tenant.TenantStatus;
import br.com.puccomp.api.shared.tenant.TenantContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolve o tenant da superfície anônima pelo slug da EJ na URL, para que nenhum service precise
 * saber de onde o tenant veio.
 *
 * <p>Roda depois do {@link BearerAuthenticationFilter} e sobrescreve o que ele fixou: em rota
 * pública quem manda é o slug, mesmo que o chamador esteja autenticado por outra EJ.
 */
@Component
@RequiredArgsConstructor
class PublicTenantFilter extends OncePerRequestFilter {

    private static final String PUBLIC_PATTERN = "/v1/public/{ejSlug}/**";

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final TenantRepository tenants;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matcher.match(PUBLIC_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String slug = matcher.extractUriTemplateVariables(PUBLIC_PATTERN, request.getRequestURI()).get("ejSlug");

        Optional<Tenant> tenant = tenants.findBySlug(slug)
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE);

        if (tenant.isEmpty()) {
            SecurityResponses.write(response, objectMapper, tracer, HttpStatus.NOT_FOUND,
                    "Empresa júnior não encontrada");
            return;
        }

        TenantContext.set(tenant.get().getId());
        filterChain.doFilter(request, response);
    }
}
