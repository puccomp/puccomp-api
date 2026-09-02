package br.com.puccomp.api.identity.dev;

import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AccountStatus;
import br.com.puccomp.api.identity.tenant.ProvisionOrganizationRequest;
import br.com.puccomp.api.identity.tenant.TenantProvisioningService;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.organization.RoleProvisioning;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
class DevDataSeeder implements ApplicationRunner {

    private static final String OWNER_EMAIL = "dono@ejcomp.dev";
    private static final String OWNER_PASSWORD = "dono123";
    private static final String OWNER_NAME = "Dono da EJ";
    private static final String MEMBER_EMAIL = "membro@ejcomp.dev";
    private static final String MEMBER_PASSWORD = "membro123";
    private static final String MEMBER_NAME = "Membro da EJ";

    private final TenantRepository tenants;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final RoleProvisioning roleProvisioning;
    private final CourseCatalog courseCatalog;
    private final MemberProvisioning memberProvisioning;
    private final TenantProvisioningService tenantProvisioningService;

    @Override
    public void run(ApplicationArguments args) {
        if (tenants.count() > 0) return;

        var ej = tenantProvisioningService.provision(new ProvisionOrganizationRequest(
                "EJ Comp",
                "ej-comp",
                OWNER_EMAIL,
                List.of(
                        "Ciência da Computação",
                        "Ciência de Dados",
                        "Engenharia de Software",
                        "Engenharia de Computação",
                        "Sistemas de Informação")));

        var owner = createAccount(OWNER_EMAIL, OWNER_PASSWORD);
        var member = createAccount(MEMBER_EMAIL, MEMBER_PASSWORD);

        TenantContext.set(ej.organization().id());
        try {
            UUID presidenteRoleId = roleProvisioning.createRole(
                    "Presidente", "Cargo de presidência da EJ");
            UUID cienciaComputacaoId = courseCatalog.listActive().getFirst().id();
            memberProvisioning.createMember(
                    owner.getId(), OWNER_NAME, cienciaComputacaoId, presidenteRoleId, Standing.OWNER);
            memberProvisioning.createMember(
                    member.getId(), MEMBER_NAME, cienciaComputacaoId, null, Standing.MEMBER);
        } finally {
            TenantContext.clear();
        }

        log.info("[dev seed] Tenant '{}' criado. Contas: dono '{}' (senha {}, standing OWNER, cargo Presidente) "
                + "e membro '{}' (senha {}, standing MEMBER, sem cargo)",
                ej.organization().slug(), OWNER_EMAIL, OWNER_PASSWORD, MEMBER_EMAIL, MEMBER_PASSWORD);
    }

    private Account createAccount(String email, String rawPassword) {
        return accounts.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(AccountStatus.ACTIVE)
                .build());
    }
}
