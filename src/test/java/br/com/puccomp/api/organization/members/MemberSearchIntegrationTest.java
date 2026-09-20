package br.com.puccomp.api.organization.members;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class MemberSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenant;
    private String token;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = seeder.seedTenant("EJ Busca Membros", "ej-busca-" + suffix);
        seeder.seedAccount(tenant, "dono-busca-" + suffix + "@ej.dev", "senha123", Standing.OWNER);
        seeder.seedMember(tenant, "João Pereira", "joao.pereira-" + suffix + "@ej.dev", Standing.MEMBER);
        seeder.seedMember(tenant, "Maria Antunes", "maria-" + suffix + "@ej.dev", Standing.MEMBER);
        token = login("dono-busca-" + suffix + "@ej.dev", "senha123");
    }

    @Test
    @DisplayName("busca sem acento encontra o nome acentuado")
    void shouldMatchAccentedNameWithPlainTerm() throws Exception {
        assertThat(namesOf("/v1/members?q=joao")).containsExactly("João Pereira");
    }

    @Test
    @DisplayName("busca alcança o e-mail, não só o nome")
    void shouldMatchEmail() throws Exception {
        assertThat(namesOf("/v1/members?q=maria")).containsExactly("Maria Antunes");
        assertThat(namesOf("/v1/members?q=joao.pereira")).containsExactly("João Pereira");
    }

    @Test
    @DisplayName("termo com menos de dois caracteres não filtra nada")
    void shouldIgnoreShortTerm() throws Exception {
        assertThat(namesOf("/v1/members?q=j")).hasSize(3);
    }

    @Test
    @DisplayName("% digitado vale como texto, não como curinga")
    void shouldEscapeWildcards() throws Exception {
        assertThat(namesOf("/v1/members?q=%25")).isEmpty();
    }

    @Test
    @DisplayName("q combina por AND com os demais filtros")
    void shouldCombineWithOtherFilters() throws Exception {
        assertThat(namesOf("/v1/members?q=joao&standing=OWNER")).isEmpty();
        assertThat(namesOf("/v1/members?q=joao&standing=MEMBER")).containsExactly("João Pereira");
    }

    @Test
    @DisplayName("o resumo agrega exatamente a população que a listagem devolve")
    void shouldAggregateTheSamePopulationAsTheListing() throws Exception {
        ResponseEntity<String> summary = getWithToken("/v1/members/summary?q=joao", token);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode total = mapper.readTree(summary.getBody()).path("total").path("value");
        assertThat(total.asInt()).isEqualTo(namesOf("/v1/members?q=joao").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("e-mail e data de entrada saem na listagem; membro nasce com joined_at conhecido")
    void shouldExposeEmailAndJoinDate() throws Exception {
        JsonNode member = contentOf("/v1/members?q=joao").get(0);

        assertThat(member.path("email").asText()).startsWith("joao.pereira-");
        assertThat(member.path("joined_at").isNull()).isFalse();
        assertThat(member.has("account_id")).isFalse();
    }

    @Test
    @DisplayName("mudar o status devolve a data de entrada, e não um campo nulo que apagaria a linha")
    void shouldKeepJoinDateOnMutationResponse() throws Exception {
        JsonNode member = contentOf("/v1/members?q=joao").get(0);
        String id = member.path("id").asText();

        ResponseEntity<String> retired = put("/v1/members/" + id + "/status",
                Map.of("value", "ALUMNUS"), token, String.class);
        assertThat(retired.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = mapper.readTree(retired.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ALUMNUS");
        assertThat(body.path("joined_at").asText()).isEqualTo(member.path("joined_at").asText());
    }

    private List<String> namesOf(String path) throws Exception {
        List<String> names = new ArrayList<>();
        contentOf(path).forEach(member -> names.add(member.path("name").asText()));
        return names;
    }

    private JsonNode contentOf(String path) throws Exception {
        ResponseEntity<String> res = getWithToken(path, token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(res.getBody()).path("content");
    }
}
