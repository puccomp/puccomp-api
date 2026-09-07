package br.com.puccomp.api.identity.tenant;

import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantDirectory implements OrganizationDirectory {

    private static final String UNKNOWN = "sua Empresa Júnior";

    private final TenantRepository tenants;

    @Override
    @Transactional(readOnly = true)
    public String currentName() {
        UUID tenantId = TenantContext.get();
        if (tenantId == null)
            return UNKNOWN;
        return tenants.findById(tenantId).map(Tenant::getName).orElse(UNKNOWN);
    }
}
