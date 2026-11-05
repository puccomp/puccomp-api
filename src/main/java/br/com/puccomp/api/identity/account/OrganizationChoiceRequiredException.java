package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.identity.tenant.OrganizationView;

import java.util.List;

public class OrganizationChoiceRequiredException extends RuntimeException {

    private final List<OrganizationView> organizations;

    public OrganizationChoiceRequiredException(List<OrganizationView> organizations) {
        super("Conta vinculada a várias EJs; informe o organization_id no login");
        this.organizations = List.copyOf(organizations);
    }

    public List<OrganizationView> getOrganizations() {
        return organizations;
    }
}
