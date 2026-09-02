package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.identity.invitation.InvitationIssuer;
import br.com.puccomp.api.organization.CourseProvisioning;
import br.com.puccomp.api.shared.exception.ConflictException;
import br.com.puccomp.api.shared.tenant.TenantContext;
import br.com.puccomp.api.shared.text.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final CourseProvisioning courseProvisioning;
    private final InvitationIssuer invitationIssuer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbc;

    public ProvisionedOrganizationResponse provision(ProvisionOrganizationRequest request) {
        String slug = Slugs.slugify(request.slug());
        if (!Slugs.isValid(slug))
            throw new IllegalArgumentException("slug: informe um valor válido");
        if (tenants.existsBySlug(slug))
            throw new ConflictException("Já existe uma EJ com esse slug");

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status -> {
                Tenant tenant = Tenant.builder()
                        .id(tenantId)
                        .name(request.name().trim())
                        .slug(slug)
                        .status(TenantStatus.ACTIVE)
                        .build();
                jdbc.update("""
                        insert into tenants (id, name, slug, status, created_at, updated_at)
                        values (?, ?, ?, ?, now(), now())
                        """, tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus().name());
                request.courses().forEach(courseProvisioning::createCourse);
                var invitation = invitationIssuer.issueForOwner(tenant.getId(), request.ownerEmail());
                return ProvisionedOrganizationResponse.from(tenant, invitation);
            });
        } finally {
            TenantContext.clear();
        }
    }
}
