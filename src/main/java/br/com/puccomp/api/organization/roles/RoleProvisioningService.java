package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.organization.RoleProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class RoleProvisioningService implements RoleProvisioning {

    private final RoleRepository roles;

    @Override
    @Transactional
    public UUID createRole(String name, String description) {
        Role role = Role.builder()
                .name(name.trim())
                .description(description)
                .build();
        return roles.save(role).getId();
    }
}
