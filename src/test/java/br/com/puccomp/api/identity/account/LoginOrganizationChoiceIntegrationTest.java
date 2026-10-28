package br.com.puccomp.api.identity.account;

import br.com.puccomp.api.organization.CourseProvisioning;
import br.com.puccomp.api.organization.MemberProvisioning;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.shared.tenant.TenantContext;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class LoginOrganizationChoiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestSeeder seeder;
    @Autowired private AccountRepository accounts;
    @Autowired private CourseProvisioning courses;
    @Autowired private MemberProvisioning members;

    @Test
    @DisplayName("login só revela EJs após validar a senha e aceita cada organização da lista")
    @SuppressWarnings("unchecked")
    void shouldChooseOrganizationAfterValidatingPassword() {
        UUID comp = seeder.seedTenant("Comp", "comp-choice");
        UUID outra = seeder.seedTenant("Outra EJ", "outra-choice");
        String email = "multi-choice@ej.dev";
        seeder.seedAccount(comp, email, "senha123", Standing.OWNER);
        UUID account = accounts.findByEmailIgnoreCase(email).orElseThrow().getId();
        TenantContext.set(outra);
        try {
            UUID course = courses.createCourse("Engenharia");
            members.createMember(account, "Pessoa", email, course, null, Standing.MEMBER);
        } finally {
            TenantContext.clear();
        }

        var wrongPassword = loginResponse(Map.of("email", email, "password", "errada"));
        assertUnauthorized(wrongPassword);

        var choice = loginResponse(Map.of("email", email, "password", "senha123"));
        assertThat(choice.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(choice.getBody()).containsOnlyKeys("status", "message", "trace_id", "timestamp", "organizations");
        assertThat(choice.getBody().get("status")).isEqualTo(409);
        assertThat(choice.getBody().get("trace_id")).isNotNull();
        assertThat(choice.getBody().get("timestamp")).isNotNull();
        assertThat((List<Map<String, Object>>) choice.getBody().get("organizations"))
                .containsExactlyInAnyOrder(
                        Map.of("id", comp.toString(), "name", "Comp", "slug", "comp-choice"),
                        Map.of("id", outra.toString(), "name", "Outra EJ", "slug", "outra-choice"));

        for (UUID organization : List.of(comp, outra)) {
            var selected = loginResponse(Map.of("email", email, "password", "senha123",
                    "organization_id", organization.toString()));
            assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
            var me = get("/v1/auth/me", (String) selected.getBody().get("access_token"),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((Map<String, Object>) me.getBody().get("organization"))
                    .containsEntry("id", organization.toString());
        }

        assertUnauthorized(loginResponse(Map.of("email", email, "password", "senha123",
                "organization_id", UUID.randomUUID().toString())));
    }

    @Test
    @DisplayName("login com uma única EJ mantém a resposta de token sem organizations")
    void shouldPreserveSingleOrganizationLogin() {
        UUID tenant = seeder.seedTenant("EJ Única", "single-choice");
        seeder.seedAccount(tenant, "single-choice@ej.dev", "senha123", Standing.OWNER);

        var response = loginResponse(Map.of("email", "single-choice@ej.dev", "password", "senha123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("access_token", "token_type", "expires_in");
    }

    private ResponseEntity<Map<String, Object>> loginResponse(Map<String, String> request) {
        return rest.exchange("/v1/auth/login", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private static void assertUnauthorized(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsOnlyKeys("status", "message", "trace_id", "timestamp")
                .containsEntry("status", 401).containsEntry("message", "Credenciais inválidas");
    }
}
