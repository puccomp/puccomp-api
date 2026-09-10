package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.organization.CourseCatalog;
import br.com.puccomp.api.organization.courses.CourseUpdateRequest;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * O retrato da EJ inteira, que nenhum processo isolado consegue dar.
 *
 * <p>A falha que estes casos perseguem não é "o número está errado", e sim a resposta que se
 * contradiz: as distribuições contam inscrições, {@code candidates} conta pessoas, e somar um no
 * outro é exatamente o erro que o contrato existe para impedir.
 */
@Import(TestSeeder.class)
class ApplicationHistorySummaryIntegrationTest extends AbstractIntegrationTest {

    /** 31/12 às 23h30 em São Paulo, mas já 01/01 em UTC: o mês só bate se agrupar no fuso da EJ. */
    private static final Instant VIRADA_DO_ANO = Instant.parse("2026-01-01T02:30:00Z");
    private static final Instant MARCO_2026 = Instant.parse("2026-03-10T15:00:00Z");
    private static final Instant ABRIL_2026 = Instant.parse("2026-04-02T15:00:00Z");

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private String token;
    private UUID antigo;
    private UUID recente;
    private UUID computacao;
    private UUID design;

    @BeforeEach
    void setUp() {
        String slug = "ej-historico-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ Historico", slug);
        seeder.seedAccount(tenantId, "dono@" + slug + ".dev", "senha123", Standing.OWNER);
        token = login("dono@" + slug + ".dev", "senha123");

        antigo = openProcess("PS 2025.2");
        recente = openProcess("PS 2026.1");

        computacao = firstCourse();
        design = seeder.seedCourse(tenantId, "Design");

        // Carla é uma pessoa e duas inscrições; os outros três são uma cada.
        insert(antigo, "Carla Souza", "carla@example.com", computacao, (short) 2, VIRADA_DO_ANO, false);
        insert(recente, "Carla Souza", "CARLA@example.com", computacao, (short) 4, MARCO_2026, false);
        insert(recente, "Bruno Lima", "bruno@example.com", design, (short) 6, MARCO_2026, true);
        insert(recente, "Diego Nulo", "diego@example.com", computacao, null, ABRIL_2026, false);
    }

    @Test
    @DisplayName("conta inscrições e pessoas separadamente, sem uma virar a outra")
    void shouldCountApplicationsAndPeopleApart() {
        JsonNode resumo = summary("");

        assertThat(resumo.path("total").path("value").asInt()).isEqualTo(4);
        assertThat(resumo.path("total").path("previous").isNull()).isTrue();

        // Quatro inscrições, três pessoas, uma delas reincidente — e 1/3, não 1/4.
        assertThat(resumo.path("candidates").path("distinct").asInt()).isEqualTo(3);
        assertThat(resumo.path("candidates").path("returning").asInt()).isEqualTo(1);
        assertThat(resumo.path("candidates").path("returning_share").asDouble())
                .isCloseTo(1d / 3, within(1e-9));

        // As distribuições fecham com o total de inscrições, não com o de pessoas.
        assertThat(sumOf(resumo.path("by_process"), "count")).isEqualTo(4);
        assertThat(sumOf(resumo.path("by_course"), "count")).isEqualTo(4);
        assertThat(sumOf(resumo.path("by_term"), "count")).isEqualTo(4);
        assertThat(sumOf(resumo.path("by_month"), "value")).isEqualTo(4);

        assertThat(resumo.path("processes_covered").asInt()).isEqualTo(2);
        assertThat(resumo.path("distinct_courses").asInt()).isEqualTo(2);
        assertThat(resumo.path("with_links").path("value").asInt()).isEqualTo(1);
        assertThat(resumo.path("median_term").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("by_process vem do mais recente para o mais antigo, e não do maior para o menor")
    void shouldOrderProcessesChronologically() {
        JsonNode byProcess = summary("").path("by_process");

        // O antigo tem menos inscrições; ainda assim vem por último, porque isto é série, não ranking.
        assertThat(byProcess).hasSize(2);
        assertThat(byProcess.get(0).path("key").path("name").asText()).isEqualTo("PS 2026.1");
        assertThat(byProcess.get(0).path("count").asInt()).isEqualTo(3);
        assertThat(byProcess.get(0).path("share").asDouble()).isCloseTo(0.75, within(1e-9));
        assertThat(byProcess.get(1).path("key").path("name").asText()).isEqualTo("PS 2025.2");
        assertThat(byProcess.get(1).path("count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a fatia de um processo navega para a listagem pelo process_id, com o mesmo total")
    void shouldDrillDownFromASliceIntoTheListing() {
        JsonNode fatia = summary("").path("by_process").get(0);
        String processId = fatia.path("key").path("id").asText();

        assertThat(listing("?process_id=" + processId).path("page").path("total_elements").asInt())
                .isEqualTo(fatia.path("count").asInt());
        assertThat(summary("?process_id=" + processId).path("total").path("value").asInt())
                .isEqualTo(fatia.path("count").asInt());

        // Recortado num processo só, o histórico deixa de enxergar a reincidência entre processos.
        JsonNode recorte = summary("?process_id=" + processId);
        assertThat(recorte.path("candidates").path("distinct").asInt()).isEqualTo(3);
        assertThat(recorte.path("candidates").path("returning").asInt()).isZero();
        assertThat(recorte.path("by_process")).hasSize(1);
    }

    @Test
    @DisplayName("cada filtro produz o mesmo total na busca e no resumo")
    void shouldMatchListingTotalForEveryFilter() {
        assertParity("");
        assertParity("?q=Carla");
        assertParity("?process_id=" + recente);
        assertParity("?course_id=" + computacao);
        assertParity("?min_term=4");
        assertParity("?max_term=2");
        assertParity("?has_cv=false");
        assertParity("?has_links=true");
        assertParity("?from=" + MARCO_2026);
        assertParity("?to=" + MARCO_2026);
        assertParity("?process_id=" + recente + "&course_id=" + computacao + "&max_term=4");
    }

    @Test
    @DisplayName("a série mensal agrupa no fuso da EJ: 31/12 às 23h30 local é dezembro")
    void shouldBucketMonthsInTheOrganizationZone() {
        JsonNode byMonth = summary("").path("by_month");

        assertThat(byMonth).hasSize(3);
        assertThat(byMonth.get(0).path("date").asText()).isEqualTo("2025-12-01");
        assertThat(byMonth.get(0).path("value").asInt()).isEqualTo(1);
        assertThat(byMonth.get(1).path("date").asText()).isEqualTo("2026-03-01");
        assertThat(byMonth.get(1).path("value").asInt()).isEqualTo(2);
        assertThat(byMonth.get(2).path("date").asText()).isEqualTo("2026-04-01");
        assertThat(byMonth.get(2).path("value").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("untouched_courses mostra o curso que a distribuição não teria como mostrar")
    void shouldListCoursesTheOrganizationNeverReached() {
        UUID engenharia = seeder.seedCourse(tenantId, "Engenharia de Software");

        JsonNode resumo = summary("");
        assertThat(names(resumo.path("untouched_courses"))).containsExactly("Engenharia de Software");
        assertThat(resumo.path("by_course")).hasSize(2);

        // Curso desativado sai dos dois lados: não é alcançável, e o histórico dele continua rotulado.
        patch("/v1/courses/" + engenharia, new CourseUpdateRequest(null, false), token, String.class);
        assertThat(summary("").path("untouched_courses")).isEmpty();

        // Filtrar por um curso deixa o outro alcançado de fora da distribuição — e não o promove a
        // "nunca alcançado", porque untouched sai do catálogo, não do complemento do filtro.
        JsonNode soComputacao = summary("?course_id=" + computacao);
        assertThat(soComputacao.path("by_course")).hasSize(1);
        assertThat(names(soComputacao.path("untouched_courses"))).containsExactly("Design");
    }

    @Test
    @DisplayName("EJ sem nenhuma inscrição responde zerado, não 404 nem nulo")
    void shouldAnswerEmptyOrganizationWithZeroes() {
        String slug = "ej-vazia-" + UUID.randomUUID().toString().substring(0, 8);
        UUID vazia = seeder.seedTenant("EJ Vazia", slug);
        seeder.seedAccount(vazia, "dono@" + slug + ".dev", "senha123", Standing.OWNER);
        String outroToken = login("dono@" + slug + ".dev", "senha123");

        JsonNode resumo = getJson("/v1/recruitment/applications/summary", outroToken);

        assertThat(resumo.path("total").path("value").asInt()).isZero();
        assertThat(resumo.path("candidates").path("distinct").asInt()).isZero();
        assertThat(resumo.path("candidates").path("returning").asInt()).isZero();
        assertThat(resumo.path("candidates").path("returning_share").isNull()).isTrue();
        assertThat(resumo.path("by_process")).isEmpty();
        assertThat(resumo.path("by_month")).isEmpty();
        assertThat(resumo.path("median_term").isNull()).isTrue();
        assertThat(resumo.path("first_submitted_at").isNull()).isTrue();
        assertThat(resumo.path("processes_covered").asInt()).isZero();
        // O catálogo nasce com o curso padrão, que nunca foi alcançado.
        assertThat(resumo.path("untouched_courses")).isNotEmpty();
    }

    @Test
    @DisplayName("o resumo não enxerga inscrição de outra EJ")
    void shouldIsolateTenants() {
        String slug = "ej-alheia-" + UUID.randomUUID().toString().substring(0, 8);
        UUID alheia = seeder.seedTenant("EJ Alheia", slug);
        seeder.seedAccount(alheia, "dono@" + slug + ".dev", "senha123", Standing.OWNER);

        assertThat(getJson("/v1/recruitment/applications/summary", login("dono@" + slug + ".dev", "senha123"))
                .path("total").path("value").asInt()).isZero();
        assertThat(summary("").path("total").path("value").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("sem autenticação é 401, sem recruitment:read é 403, e a resposta não é cacheada")
    void shouldRequireAuthorization() {
        String path = "/v1/recruitment/applications/summary";
        assertThat(getWithToken(path, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(path, token).getHeaders().getCacheControl())
                .isEqualTo("private, no-store");

        String slug = "ej-sem-perm-" + UUID.randomUUID().toString().substring(0, 8);
        UUID outra = seeder.seedTenant("EJ Sem Permissao", slug);
        seeder.seedAccount(outra, "membro@" + slug + ".dev", "senha123", Standing.MEMBER);

        assertThat(getWithToken(path, login("membro@" + slug + ".dev", "senha123")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("busca e resumo rejeitam os mesmos filtros inválidos")
    void shouldRejectTheSameInvalidFilters() {
        for (String query : List.of("?process_id=nao-e-uuid", "?course_id=nao-e-uuid", "?min_term=abc",
                "?has_links=talvez", "?from=ontem")) {
            assertThat(getWithToken("/v1/recruitment/applications" + query, token).getStatusCode())
                    .as("busca %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(getWithToken("/v1/recruitment/applications/summary" + query, token).getStatusCode())
                    .as("resumo %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("page, size e sort não alteram o resumo")
    void shouldIgnorePagination() {
        assertThat(summary("?page=1&size=1&sort=email,asc")).isEqualTo(summary(""));
    }

    private void assertParity(String query) {
        long naBusca = listing(query).path("page").path("total_elements").asLong();
        long noResumo = summary(query).path("total").path("value").asLong();
        assertThat(noResumo).as("resumo e busca para %s", query.isEmpty() ? "(sem filtro)" : query)
                .isEqualTo(naBusca);
    }

    private JsonNode listing(String query) {
        return getJson("/v1/recruitment/applications" + query, token);
    }

    private JsonNode summary(String query) {
        return getJson("/v1/recruitment/applications/summary" + query, token);
    }

    private static long sumOf(JsonNode distribution, String field) {
        long sum = 0;
        for (JsonNode entry : distribution) sum += entry.path(field).asLong();
        return sum;
    }

    private static List<String> names(JsonNode refs) {
        return refs.findValuesAsText("name");
    }

    /**
     * Inserção direta porque o fixture precisa de {@code created_at} escolhido: a virada do ano só
     * pode ser verificada com um instante na borda da meia-noite local, e o endpoint público carimba
     * o instante da requisição.
     */
    private void insert(UUID processId, String fullName, String email, UUID courseId, Short term,
                        Instant createdAt, boolean withLink) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into candidate_applications (id, tenant_id, process_id, full_name, email, phone,
                    course_id, current_term, privacy_consent_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, '31999998888', ?, ?, ?, ?, ?)
                """, id, tenantId, processId, fullName, email, courseId, term,
                Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
        if (withLink)
            jdbc.update("insert into candidate_application_links (application_id, link_order, url) "
                    + "values (?, 0, 'https://github.com/exemplo')", id);
    }

    private UUID firstCourse() {
        return get("/v1/courses", token,
                new ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
    }

    private UUID openProcess(String title) {
        UUID id = post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + id + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);
        return id;
    }

    private JsonNode getJson(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        String body = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
