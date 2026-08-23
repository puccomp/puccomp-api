package br.com.puccomp.api.organization.members;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class MemberPaginationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("listagem de membros retorna PagedModel com tamanho padrão 20")
    void shouldReturnPagedModelFormatWithDefaultSize() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Membros", "ej-pg-membros-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgm@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgm@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/members", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(res.getBody());
        assertThat(body.has("content")).isTrue();
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.path("page").has("size")).isTrue();
        assertThat(body.path("page").has("total_elements")).isTrue();
        assertThat(body.path("page").has("total_pages")).isTrue();
        assertThat(body.path("page").has("number")).isTrue();
        assertThat(body.has("pageable")).isFalse();
        assertThat(body.has("totalElements")).isFalse();
        assertThat(body.path("page").get("size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("tamanho máximo de página é limitado a 100")
    void shouldCapPageSizeAt100() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Membros Limite", "ej-pg-membros-limite-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgm2@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgm2@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/members?size=500", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(res.getBody());
        assertThat(body.path("page").get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("sem sort informado, ordena por nome ASC")
    void shouldSortByNameAscByDefault() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Membros Ordem", "ej-pg-membros-ordem-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgm3@ej.dev", "senha123", Standing.OWNER);
        seeder.seedAccount(tenant, "membro-a-pgm3@ej.dev", "senha123", Standing.MEMBER);
        seeder.seedAccount(tenant, "membro-b-pgm3@ej.dev", "senha123", Standing.MEMBER);
        String token = login("dono-pgm3@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/members?size=100", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode content = mapper.readTree(res.getBody()).get("content");
        List<String> nomes = new ArrayList<>();
        content.forEach(node -> nomes.add(node.get("name").asText()));

        List<String> ordenado = new ArrayList<>(nomes);
        ordenado.sort(String::compareTo);

        assertThat(nomes).isEqualTo(ordenado);
    }
}