package br.com.puccomp.api.identity.tenant;

import java.util.UUID;

/**
 * A EJ como o contrato a expõe. Por dentro ela é o {@code Tenant}, mas multi-tenancy é decisão de
 * hospedagem: para quem consome a API existe uma organização, com nome e slug próprios.
 *
 * <p>O {@code slug} é o mesmo que abre as rotas anônimas ({@code /v1/public/{orgSlug}/...}), então
 * quem é membro consegue montar o link público da própria EJ.
 */
public record OrganizationView(UUID id, String name, String slug) {

    public static OrganizationView from(Tenant tenant) {
        return new OrganizationView(tenant.getId(), tenant.getName(), tenant.getSlug());
    }
}
