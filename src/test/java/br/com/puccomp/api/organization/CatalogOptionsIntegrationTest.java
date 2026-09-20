package br.com.puccomp.api.organization;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catálogo para preencher filtro. Paginar aqui deixaria a lista incompleta em silêncio numa EJ
 * grande, que é o pior jeito de quebrar — o seletor mostraria menos cargos do que existem sem
 * nenhum sinal de que faltou alguém.
 */
@Import(TestSeeder.class)
class CatalogOptionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = seeder.seedTenant("EJ Catalogo", "ej-catalogo-" + sufixo);
        seeder.seedAccount(tenant, "dono-" + sufixo + "@ej.dev", "senha123", Standing.OWNER);
        token = login("dono-" + sufixo + "@ej.dev", "senha123");
    }

    @Test
    @DisplayName("as opções vêm inteiras e sem envelope de página, mesmo passando do tamanho padrão")
    void shouldReturnEveryOptionWithoutPaging() throws Exception {
        for (int i = 0; i < 25; i++)
            post("/v1/roles", Map.of("name", "Cargo %02d".formatted(i), "description", "x"), token,
                    String.class);

        JsonNode opcoes = mapper.readTree(getWithToken("/v1/roles/options", token).getBody());

        assertThat(opcoes.isArray()).isTrue();
        assertThat(opcoes).hasSize(25);
        assertThat(opcoes.get(0).path("name").asText()).isEqualTo("Cargo 00");

        // A listagem paginada continua existindo, com envelope e teto — são coisas diferentes.
        JsonNode pagina = mapper.readTree(getWithToken("/v1/roles", token).getBody());
        assertThat(pagina.path("content").size()).isEqualTo(20);
    }

    @Test
    @DisplayName("diretoria desativada sai do seletor: ela não é uma opção válida")
    void shouldHideInactiveDepartments() throws Exception {
        UUID ativa = idOf(post("/v1/departments", Map.of("name", "Comercial", "description", "x"),
                token, String.class).getBody());
        UUID inativa = idOf(post("/v1/departments", Map.of("name", "Extinta", "description", "x"),
                token, String.class).getBody());
        // Diretoria ainda não tem rota de atualização; o que este teste cobre é a consulta.
        jdbc.update("update departments set active = false where id = ?", inativa);

        JsonNode opcoes = mapper.readTree(getWithToken("/v1/departments/options", token).getBody());

        assertThat(opcoes).hasSize(1);
        assertThat(opcoes.get(0).path("id").asText()).isEqualTo(ativa.toString());
    }

    private UUID idOf(String body) throws Exception {
        return UUID.fromString(mapper.readTree(body).path("id").asText());
    }
}
