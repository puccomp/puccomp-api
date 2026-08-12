package br.com.puccomp.api.support;

import br.com.puccomp.api.identity.account.Account;
import br.com.puccomp.api.identity.account.AccountRepository;
import br.com.puccomp.api.identity.account.AccountStatus;
import br.com.puccomp.api.identity.tenant.Tenant;
import br.com.puccomp.api.identity.tenant.TenantRepository;
import br.com.puccomp.api.identity.tenant.TenantStatus;
import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.organization.CourseProvisioning;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.organization.RoleProvisioning;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@TestComponent
public class TestSeeder {

    private final TenantRepository tenants;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final RoleProvisioning roleProvisioning;
    private final CourseProvisioning courseProvisioning;
    private final CourseCatalog courseCatalog;
    private final MemberProvisioning memberProvisioning;

    public TestSeeder(TenantRepository tenants, AccountRepository accounts, PasswordEncoder passwordEncoder,
                      RoleProvisioning roleProvisioning, CourseProvisioning courseProvisioning,
                      CourseCatalog courseCatalog, MemberProvisioning memberProvisioning) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.roleProvisioning = roleProvisioning;
        this.courseProvisioning = courseProvisioning;
        this.courseCatalog = courseCatalog;
        this.memberProvisioning = memberProvisioning;
    }

    public UUID seedTenant(String name, String slug) {
        return tenants.save(Tenant.builder()
                .name(name).slug(slug).status(TenantStatus.ACTIVE).build()).getId();
    }

    public UUID seedAccount(UUID tenantId, String email, String rawPassword, Standing standing) {
        UUID accountId = accounts.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(AccountStatus.ACTIVE)
                .build()).getId();
        TenantContext.set(tenantId);
        try {
            UUID courseId = courseCatalog.listActive().stream().findFirst()
                    .map(CourseCatalog.CourseOption::id)
                    .orElseGet(() -> courseProvisioning.createCourse("Ciência da Computação"));
            return memberProvisioning.createMember(accountId, email, courseId, null, standing);
        } finally {
            TenantContext.clear();
        }
    }

    public UUID seedCourse(UUID tenantId, String name) {
        TenantContext.set(tenantId);
        try {
            return courseProvisioning.createCourse(name);
        } finally {
            TenantContext.clear();
        }
    }

    public UUID seedCargo(UUID tenantId, String name) {
        TenantContext.set(tenantId);
        try {
            return roleProvisioning.createRole(name, name);
        } finally {
            TenantContext.clear();
        }
    }
}
