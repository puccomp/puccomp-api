package br.com.puccomp.api.identity.account;

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

/**
 * O {@code /v1/auth/me} é o contexto de sessão: é dele que qualquer cliente — painel ou servidor
 * MCP — tira o que o sujeito pode fazer. Se as permissões daqui divergirem das que o filtro aplica,
 * o cliente habilita botão que devolve 403.
 */
@Import(TestSeeder.class)
class MeContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("devolve a organização com id, nome e slug — o slug abre as rotas públicas da própria EJ")
    void shouldExposeOrganization() {
        UUID tenant = seeder.seedTenant("EJ Contexto", "ej-contexto");
        seeder.seedAccount(tenant, "dono-ctx@ej.dev", "senha123", Standing.OWNER);

        Map<String, Object> body = me(login("dono-ctx@ej.dev", "senha123"));

        assertThat(body.get("organization")).isEqualTo(Map.of(
                "id", tenant.toString(), "name", "EJ Contexto", "slug", "ej-contexto"));
    }

    @Test
    @DisplayName("OWNER recebe o catálogo inteiro de permissões, sem o standing vazando como ROLE_")
    void shouldGiveOwnerEveryPermission() {
        UUID tenant = seeder.seedTenant("EJ Dono", "ej-dono-ctx");
        seeder.seedAccount(tenant, "dono-tudo@ej.dev", "senha123", Standing.OWNER);
        String owner = login("dono-tudo@ej.dev", "senha123");

        assertThat(permissionsOf(me(owner)))
                .containsExactlyInAnyOrderElementsOf(catalog(owner))
                .isNotEmpty()
                .noneMatch(p -> p.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("membro só enxerga o que lhe foi concedido, e o /me acompanha a concessão")
    void shouldReflectMemberGrants() {
        UUID tenant = seeder.seedTenant("EJ Membro", "ej-membro-ctx");
        seeder.seedAccount(tenant, "dono-membro@ej.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro-ctx@ej.dev", "senha123", Standing.MEMBER);
        String owner = login("dono-membro@ej.dev", "senha123");

        assertThat(permissionsOf(me(login("membro-ctx@ej.dev", "senha123")))).isEmpty();

        grant(owner, memberId, List.of("roles:read"));

        assertThat(permissionsOf(me(login("membro-ctx@ej.dev", "senha123")))).containsExactly("roles:read");
    }

    @Test
    @DisplayName("PAT escopado devolve o teto real do token, não as permissões da conta")
    void shouldCapPatToItsScopes() {
        UUID tenant = seeder.seedTenant("EJ PAT", "ej-pat-ctx");
        seeder.seedAccount(tenant, "dono-pat@ej.dev", "senha123", Standing.OWNER);
        String owner = login("dono-pat@ej.dev", "senha123");

        assertThat(permissionsOf(me(pat(owner, List.of("members:read")))))
                .containsExactly("members:read");
    }

    @Test
    @DisplayName("perfil do membro traz curso, cargo e diretoria como referência {id, name}")
    void shouldExposeMemberProfileAsReferences() {
        UUID tenant = seeder.seedTenant("EJ Perfil", "ej-perfil-ctx");
        UUID cargo = seeder.seedCargo(tenant, "Diretor de Projetos");
        seeder.seedAccount(tenant, "dono-perfil@ej.dev", "senha123", Standing.OWNER, cargo);

        @SuppressWarnings("unchecked")
        Map<String, Object> member = (Map<String, Object>) me(login("dono-perfil@ej.dev", "senha123")).get("member");

        assertThat(member.get("role")).isEqualTo(Map.of("id", cargo.toString(), "name", "Diretor de Projetos"));
        assertThat(member.get("course")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKeys("id", "name");
    }

    private Map<String, Object> me(String token) {
        ResponseEntity<Map<String, Object>> res = get("/v1/auth/me", token,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<String> permissionsOf(Map<String, Object> me) {
        return (List<String>) me.get("permissions");
    }

    private List<String> catalog(String token) {
        ResponseEntity<Map<String, Object>> res = get("/v1/permissions", token,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return permissionsOf(res.getBody());
    }

    private String pat(String ownerToken, List<String> scopes) {
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/pat", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "automacao", "scopes", scopes), jsonHeaders(ownerToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("token");
    }

    private void grant(String ownerToken, UUID memberId, List<String> codes) {
        ResponseEntity<String> res = rest.exchange("/v1/members/" + memberId + "/permissions", HttpMethod.PUT,
                new HttpEntity<>(Map.of("permissions", codes), jsonHeaders(ownerToken)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
