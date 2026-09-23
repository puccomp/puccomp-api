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
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tempo de casa é decorrido: quem está ativo conta até agora, quem saiu conta até a saída. Não é a
 * permanência média do relatório histórico, que só soma intervalos encerrados dentro da janela.
 */
@Import(TestSeeder.class)
class MemberTenureIntegrationTest extends AbstractIntegrationTest {

    /** Dias por mês civil médio, o mesmo 365,2425/12 que a API usa. */
    private static final double DIAS_POR_MES = 365.2425 / 12;

    @Autowired
    TestSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenant;
    private UUID curso;
    private String token;

    @BeforeEach
    void setUp() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        tenant = seeder.seedTenant("EJ Tempo de Casa", "ej-tenure-" + sufixo);
        UUID dono = seeder.seedAccount(tenant, "dono-" + sufixo + "@ej.dev", "senha123", Standing.OWNER);
        token = login("dono-" + sufixo + "@ej.dev", "senha123");

        curso = jdbc.queryForObject("select course_id from members where id = ?", UUID.class, dono);
        // O dono entrou agora e não deve mexer na mediana dos outros: sai do recorte pelo standing.
        membro("Doze Meses", MemberStatus.ACTIVE, mesesAtras(12), null);
        membro("Seis Meses", MemberStatus.ACTIVE, mesesAtras(6), null);
        membro("Dois Meses", MemberStatus.ACTIVE, mesesAtras(2), null);
        membro("Saiu Aos Quatro", MemberStatus.ALUMNUS, mesesAtras(10), mesesAtras(6));
        membro("Sem Data", MemberStatus.ACTIVE, null, null);
    }

    @Test
    @DisplayName("mediana ignora quem não tem data de entrada, e publica quantos ficaram de fora")
    void shouldComputeMedianOverKnownStartsOnly() throws Exception {
        JsonNode tenure = summary("?standing=MEMBER").path("tenure");

        // 12, 6, 4 e 2 meses: mediana é a média dos dois centrais, (6 + 4) / 2.
        assertThat(tenure.path("median_months").asDouble()).isEqualTo(5.0, within(0.2));
        assertThat(tenure.path("average_months").asDouble()).isEqualTo(6.0, within(0.2));
        assertThat(tenure.path("unknown_start").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("o recorte muda a conta: ex-membros contam até a saída, não até agora")
    void shouldMeasureClosedTenureForAlumni() throws Exception {
        JsonNode alumni = summary("?status=ALUMNUS").path("tenure");

        assertThat(alumni.path("median_months").asDouble()).isEqualTo(4.0, within(0.2));
        assertThat(alumni.path("unknown_start").asInt()).isZero();
    }

    @Test
    @DisplayName("tenure é null quando ninguém do recorte tem entrada conhecida")
    void shouldReturnNullTenureWithoutEligibleMembers() throws Exception {
        JsonNode tenure = summary("?q=Sem Data").path("tenure");

        assertThat(tenure.path("median_months").isNull()).isTrue();
        assertThat(tenure.path("average_months").isNull()).isTrue();
        assertThat(tenure.path("unknown_start").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("left_at sai na listagem e é nulo em quem está ativo")
    void shouldExposeLeftAt() throws Exception {
        JsonNode alumnus = membroChamado("Quatro");
        JsonNode ativo = membroChamado("Doze");

        assertThat(alumnus.path("left_at").isNull()).isFalse();
        assertThat(ativo.path("left_at").isNull()).isTrue();
        assertThat(ativo.path("joined_at").isNull()).isFalse();
    }

    @Test
    @DisplayName("ordenar por joined_at usa o nome público do campo, com a data desconhecida no fim")
    void shouldSortByJoinDate() throws Exception {
        // Nos dois sentidos: no padrão do Postgres o nulo viria primeiro em desc, e "quem entrou
        // por último" começaria por quem já estava na EJ antes do rastreamento.
        assertThat(nomes("/v1/members?standing=MEMBER&sort=joined_at,desc"))
                .containsExactly("Dois Meses", "Seis Meses", "Saiu Aos Quatro", "Doze Meses", "Sem Data");
        assertThat(nomes("/v1/members?standing=MEMBER&sort=joined_at,asc"))
                .containsExactly("Doze Meses", "Saiu Aos Quatro", "Seis Meses", "Dois Meses", "Sem Data");
    }

    @Test
    @DisplayName("left_at ordena quem saiu e deixa no fim quem continua ativo")
    void shouldSortByLeaveDate() throws Exception {
        assertThat(nomes("/v1/members?standing=MEMBER&sort=left_at,desc").getFirst())
                .isEqualTo("Saiu Aos Quatro");
    }

    @Test
    @DisplayName("empate no campo pedido é desfeito pelo id, e a paginação não repete nem pula ninguém")
    void shouldBreakTiesById() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int page = 0; page < 5; page++)
            mapper.readTree(getWithToken("/v1/members?standing=MEMBER&sort=status,asc&size=1&page=" + page,
                    token).getBody()).path("content").forEach(member -> ids.add(member.path("id").asText()));

        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
    }

    private JsonNode summary(String query) throws Exception {
        var res = getWithToken("/v1/members/summary" + query, token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(res.getBody());
    }

    private JsonNode membroChamado(String nome) throws Exception {
        var res = getWithToken("/v1/members?q=" + nome, token);
        return mapper.readTree(res.getBody()).path("content").get(0);
    }

    private List<String> nomes(String path) throws Exception {
        List<String> nomes = new ArrayList<>();
        mapper.readTree(getWithToken(path, token).getBody()).path("content")
                .forEach(member -> nomes.add(member.path("name").asText()));
        return nomes;
    }

    private static Instant mesesAtras(int meses) {
        return Instant.now().minus((long) (meses * DIAS_POR_MES), ChronoUnit.DAYS);
    }

    private void membro(String nome, MemberStatus status, Instant entrada, Instant saida) {
        jdbc.update("""
                insert into members (id, tenant_id, name, standing, status, course_id, joined_at, left_at)
                values (?, ?, ?, 'MEMBER', ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant, nome, status.name(), curso,
                entrada == null ? null : Timestamp.from(entrada),
                saida == null ? null : Timestamp.from(saida));
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
