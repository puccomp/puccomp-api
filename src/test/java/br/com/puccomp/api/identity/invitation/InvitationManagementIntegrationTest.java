package br.com.puccomp.api.identity.invitation;

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
class InvitationManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @Test
    @DisplayName("ciclo do convite: criar → listar (PENDING) → reenviar → revogar (some da lista)")
    void shouldManageInvitationLifecycle() {
        UUID tenant = seeder.seedTenant("EJ Convite", "ej-conv");
        seeder.seedAccount(tenant, "dono@convite.dev", "senha123", Standing.OWNER);
        String owner = login("dono@convite.dev", "senha123");

        String invitationId = criarConvite(owner, "novato@convite.dev");

        ResponseEntity<String> lista = getWithToken("/v1/invitations", owner);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getBody()).contains("novato@convite.dev").contains("PENDING");

        ResponseEntity<Map<String, Object>> reenvio = rest.exchange(
                "/v1/invitations/" + invitationId + "/resend", HttpMethod.POST,
                new HttpEntity<>(bearer(owner)), map());
        assertThat(reenvio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) reenvio.getBody().get("token_prefix")).startsWith("inv_");
        assertThat(reenvio.getBody().get("status")).isEqualTo("PENDING");

        ResponseEntity<Void> revoke = rest.exchange("/v1/invitations/" + invitationId,
                HttpMethod.DELETE, new HttpEntity<>(bearer(owner)), Void.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getWithToken("/v1/invitations", owner).getBody()).doesNotContain("novato@convite.dev");
    }

    @Test
    @DisplayName("convite de outra EJ é invisível: revogar cross-tenant retorna 404")
    void shouldNotRevokeOtherTenantInvitation() {
        UUID tenantA = seeder.seedTenant("EJ A", "ej-a-inv");
        UUID tenantB = seeder.seedTenant("EJ B", "ej-b-inv");
        seeder.seedAccount(tenantA, "dono-a@convite.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenantB, "dono-b@convite.dev", "senha123", Standing.OWNER);

        String invitationB = criarConvite(login("dono-b@convite.dev", "senha123"), "alvo@convite.dev");

        ResponseEntity<Void> res = rest.exchange("/v1/invitations/" + invitationB, HttpMethod.DELETE,
                new HttpEntity<>(bearer(login("dono-a@convite.dev", "senha123"))), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("standing inválido no corpo: 400 com mensagem limpa, sem vazar classe interna")
    void shouldReturnCleanMessageForInvalidEnum() {
        UUID tenant = seeder.seedTenant("EJ Enum", "ej-enum");
        seeder.seedAccount(tenant, "dono@enum.dev", "senha123", Standing.OWNER);
        String owner = login("dono@enum.dev", "senha123");

        HttpHeaders headers = bearer(owner);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange("/v1/invitations", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "x@enum.dev", "standing", "BANANA"), headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("MEMBER").doesNotContain("br.com.puccomp");
    }

    @Test
    @DisplayName("MEMBER com members:invite não pode convidar com standing OWNER: 403")
    void shouldForbidMemberFromInvitingOwnerStanding() {
        UUID tenant = seeder.seedTenant("EJ Escala", "ej-escala");
        seeder.seedAccount(tenant, "dono@escala.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "member@escala.dev", "senha123", Standing.MEMBER);
        String owner = login("dono@escala.dev", "senha123");
        grant(owner, memberId, "members:invite");

        HttpHeaders headers = bearer(login("member@escala.dev", "senha123"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange("/v1/invitations", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "novo@escala.dev", "standing", "OWNER"), headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void grant(String ownerToken, UUID memberId, String permission) {
        HttpHeaders headers = bearer(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange("/v1/members/" + memberId + "/permissions",
                HttpMethod.PUT, new HttpEntity<>(Map.of("permissions", List.of(permission)), headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String criarConvite(String ownerToken, String email) {
        HttpHeaders headers = bearer(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/invitations", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "standing", "MEMBER"), headers), map());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("id");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ParameterizedTypeReference<Map<String, Object>> map() {
        return new ParameterizedTypeReference<>() { };
    }
}
