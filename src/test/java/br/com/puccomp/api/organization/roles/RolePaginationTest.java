package br.com.puccomp.api.organization.roles;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class RolePaginationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deveRetornarFormatoPagedModelComTamanhoPadrao() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Cargos", "ej-pg-cargos-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgr@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgr@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/roles", token);
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
    void deveLimitarTamanhoMaximoDaPaginaEm100() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Cargos Limite", "ej-pg-cargos-limite-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgr2@ej.dev", "senha123", Standing.OWNER);
        String token = login("dono-pgr2@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/roles?size=500", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(res.getBody());
        assertThat(body.path("page").get("size").asInt()).isEqualTo(100);
    }

    @Test
    void deveOrdenarPorNomeAscQuandoSortNaoInformado() throws Exception {
        UUID tenant = seeder.seedTenant("EJ Paginacao Cargos Ordem", "ej-pg-cargos-ordem-" + UUID.randomUUID());
        seeder.seedAccount(tenant, "dono-pgr3@ej.dev", "senha123", Standing.OWNER);
        seeder.seedCargo(tenant, "Zeta");
        seeder.seedCargo(tenant, "Alfa");
        String token = login("dono-pgr3@ej.dev", "senha123");

        ResponseEntity<String> res = getWithToken("/v1/roles?size=100", token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode content = mapper.readTree(res.getBody()).get("content");
        List<String> nomes = new ArrayList<>();
        content.forEach(node -> nomes.add(node.get("name").asText()));

        List<String> ordenado = new ArrayList<>(nomes);
        ordenado.sort(String::compareTo);

        assertThat(nomes).isEqualTo(ordenado);
    }
}