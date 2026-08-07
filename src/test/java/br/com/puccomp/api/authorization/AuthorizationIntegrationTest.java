package br.com.puccomp.api.authorization;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("cargos são isolados entre tenants")
    void shouldIsolateRolesBetweenTenants() {
        UUID tenantA = seeder.seedTenant("EJ A", "ej-a");
        UUID tenantB = seeder.seedTenant("EJ B", "ej-b");
        seeder.seedAccount(tenantA, "dono-a@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenantB, "dono-b@ej.dev", "senha123", Standing.OWNER);
        seeder.seedCargo(tenantA, "Diretor A");
        seeder.seedCargo(tenantB, "Diretor B");

        ResponseEntity<String> res = getWithToken("/v1/roles", login("dono-a@ej.dev", "senha123"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("Diretor A").doesNotContain("Diretor B");
    }

    @Test
    @DisplayName("membro sem permissão: 403; owner bypassa; grant na conta libera")
    void shouldEnforcePermissionBarrier() {
        UUID tenant = seeder.seedTenant("EJ C", "ej-c");
        seeder.seedAccount(tenant, "dono-c@ej.dev", "senha123", Standing.OWNER);
        UUID membroMemberId = seeder.seedAccount(tenant, "membro-c@ej.dev", "senha123", Standing.MEMBER);

        String membro = login("membro-c@ej.dev", "senha123");
        String owner = login("dono-c@ej.dev", "senha123");

        assertThat(getWithToken("/v1/roles", membro).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken("/v1/roles", owner).getStatusCode()).isEqualTo(HttpStatus.OK);

        concederPermissoesDoMembro(owner, membroMemberId, List.of("roles:read"));
        assertThat(getWithToken("/v1/roles", membro).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("PAT escopado limita até um OWNER ao teto de permissões")
    void shouldCapOwnerPatToItsScopes() {
        UUID tenant = seeder.seedTenant("EJ D", "ej-d");
        seeder.seedAccount(tenant, "dono-d@ej.dev", "senha123", Standing.OWNER);
        String owner = login("dono-d@ej.dev", "senha123");

        String pat = criarPat(owner, List.of("members:read"));

        assertThat(getWithToken("/v1/members", pat).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getWithToken("/v1/roles", pat).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String criarPat(String ownerToken, List<String> scopes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/pat", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "automacao", "scopes", scopes), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("token");
    }

    private void concederPermissoesDoMembro(String ownerToken, UUID memberId, List<String> codigos) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange("/v1/authz/members/" + memberId + "/permissions",
                HttpMethod.PUT, new HttpEntity<>(Map.of("permissions", codigos), headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
