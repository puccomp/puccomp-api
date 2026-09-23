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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class MemberRemovalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenant;
    private String owner;
    private UUID membro;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = UUID.randomUUID().toString().substring(0, 8);
        tenant = seeder.seedTenant("EJ Remocao", "ej-remocao-" + sufixo);
        seeder.seedAccount(tenant, "dono-" + sufixo + "@ej.dev", "senha123", Standing.OWNER);
        membro = seeder.seedMember(tenant, "Maria Antunes", "maria-" + sufixo + "@ej.dev",
                Standing.MEMBER);
        owner = login("dono-" + sufixo + "@ej.dev", "senha123");
    }

    @Test
    @DisplayName("removido some da listagem, do resumo e do GET por id")
    void shouldDisappearFromEveryRead() throws Exception {
        assertThat(delete("/v1/members/" + membro, owner, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(getWithToken("/v1/members/" + membro, owner).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(namesOf("/v1/members")).doesNotContain("Maria Antunes");

        JsonNode resumo = mapper.readTree(getWithToken("/v1/members/summary", owner).getBody());
        assertThat(resumo.path("total").path("value").asInt()).isEqualTo(1);
        assertThat(resumo.path("by_status").toString()).doesNotContain("Maria");
    }

    @Test
    @DisplayName("remover duas vezes é 404 na segunda: o membro já não existe para a API")
    void shouldRejectTheSecondRemoval() {
        assertThat(delete("/v1/members/" + membro, owner, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete("/v1/members/" + membro, owner, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("include_deleted traz quem saiu, e restore o devolve à listagem")
    void shouldListAndRestoreRemovedMembers() throws Exception {
        delete("/v1/members/" + membro, owner, Void.class);

        assertThat(namesOf("/v1/members?include_deleted=true")).contains("Maria Antunes");

        // O resumo aceita o mesmo parâmetro, senão a tabela e os números ao lado dela passariam a
        // descrever populações diferentes.
        JsonNode comRemovidos = mapper.readTree(
                getWithToken("/v1/members/summary?include_deleted=true", owner).getBody());
        assertThat(comRemovidos.path("total").path("value").asInt()).isEqualTo(2);

        ResponseEntity<String> restaurado = post("/v1/members/" + membro + "/restore", null, owner,
                String.class);
        assertThat(restaurado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(restaurado.getBody()).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(namesOf("/v1/members")).contains("Maria Antunes");
    }

    @Test
    @DisplayName("include_deleted exige members:write; só ler não basta")
    void shouldRequireWriteToSeeRemovedMembers() {
        seeder.seedMember(tenant, "Leitor Simples", "leitor-" + sufixo + "@ej.dev", Standing.MEMBER);
        String leitor = grantReadOnly();

        assertThat(getWithToken("/v1/members?include_deleted=true", leitor).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getWithToken("/v1/members", leitor).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("o token do removido para de valer na hora, mesmo emitido antes")
    void shouldRevokeAccessOfTheRemovedMember() {
        String dela = login("maria-" + sufixo + "@ej.dev", "senha123");
        delete("/v1/members/" + membro, owner, Void.class);

        assertThat(getWithToken("/v1/members", dela).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("remover registra a saída no histórico: sem ela o intervalo ativo nunca fecharia")
    void shouldRecordTheExitInTheHistory() {
        delete("/v1/members/" + membro, owner, Void.class);

        assertThat(kindsOf(membro)).containsExactly("CREATED", "DELETED");

        post("/v1/members/" + membro + "/restore", null, owner, String.class);
        assertThat(kindsOf(membro)).containsExactly("CREATED", "DELETED", "RESTORED");
    }

    private java.util.List<String> kindsOf(UUID memberId) {
        return jdbc.queryForList(
                "select kind from member_status_history where member_id = ? order by sequence",
                String.class, memberId);
    }

    @Test
    @DisplayName("definir status de um removido é 404: ele não existe para a API")
    void shouldRejectStatusChangeOnRemovedMember() {
        delete("/v1/members/" + membro, owner, Void.class);

        assertThat(put("/v1/members/" + membro + "/status", Map.of("value", "ACTIVE"), owner,
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Um membro comum recebe members:read pelo grant individual; segue sem members:write. */
    private String grantReadOnly() {
        UUID leitorId = memberIdOf("Leitor Simples");
        put("/v1/members/" + leitorId + "/permissions",
                Map.of("permissions", java.util.List.of("members:read")), owner, String.class);
        return login("leitor-" + sufixo + "@ej.dev", "senha123");
    }

    private UUID memberIdOf(String nome) {
        for (JsonNode member : contentOf("/v1/members"))
            if (nome.equals(member.path("name").asText()))
                return UUID.fromString(member.path("id").asText());
        throw new AssertionError("membro não encontrado: " + nome);
    }

    private java.util.List<String> namesOf(String path) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        contentOf(path).forEach(member -> names.add(member.path("name").asText()));
        return names;
    }

    private JsonNode contentOf(String path) {
        try {
            ResponseEntity<String> res = getWithToken(path, owner);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            return mapper.readTree(res.getBody()).path("content");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
