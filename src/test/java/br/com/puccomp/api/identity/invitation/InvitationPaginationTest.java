package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class InvitationPaginationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("listagem de convites retorna PagedModel com tamanho padrão 20")
    void shouldReturnPagedModelFormatWithDefaultSize() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Convites", "ej-pg-convites-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgi@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgi@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/invitations", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(res.getBody());
        assertThat(body.has("content")).isTrue();
        assertThat(body.path("page").has("size")).isTrue();
        assertThat(body.path("page").has("total_elements")).isTrue();
        assertThat(body.path("page").has("total_pages")).isTrue();
        assertThat(body.path("page").has("number")).isTrue();
        assertThat(body.path("page").get("size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("tamanho máximo de página é limitado a 100")
    void shouldCapPageSizeAt100() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Convites Limite", "ej-pg-convites-limite-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgi2@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgi2@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/invitations?size=500", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(res.getBody());
        assertThat(body.path("page").get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("sem sort informado, ordena por created_at DESC")
    void shouldSortByCreatedAtDescByDefault() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Convites Ordem", "ej-pg-convites-ordem-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgi3@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgi3@ej.dev", "senha123");

        criarConvite(token, "primeiro@convite.dev");
        criarConvite(token, "segundo@convite.dev");

        ResponseEntity<String> res = getWithToken("/v1/invitations?size=100", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode content = mapper.readTree(res.getBody()).get("content");

        assertThat(content.get(0).get("email").asText()).isEqualTo("segundo@convite.dev");
        assertThat(content.get(1).get("email").asText()).isEqualTo("primeiro@convite.dev");
    }

    private void criarConvite(String ownerToken, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = rest.exchange("/v1/invitations", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "standing", "STAFF"), headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}