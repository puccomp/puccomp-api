package br.com.puccomp.api.config;

import br.com.puccomp.api.shared.tenant.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MultiTenancyConfig {

    private static final UUID NO_TENANT = new UUID(0L, 0L);

    @Bean
    HibernatePropertiesCustomizer multiTenancyCustomizer() {
        CurrentTenantIdentifierResolver<UUID> resolver = new CurrentTenantIdentifierResolver<>() {
            @Override
            public UUID resolveCurrentTenantIdentifier() {
                var tenantId = TenantContext.get();
                return tenantId != null ? tenantId : NO_TENANT;
            }

            @Override
            public boolean validateExistingCurrentSessions() { return false; }
        };
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
