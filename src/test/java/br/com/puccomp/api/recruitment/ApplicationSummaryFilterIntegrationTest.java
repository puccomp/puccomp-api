package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.organization.CourseCatalog;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * O resumo é irmão da listagem: descreve exatamente as inscrições que a tabela ao lado mostra.
 *
 * <p>Cada caso compara os dois endpoints com o mesmo recorte, porque a falha que interessa não é
 * "o número está errado" e sim "a tabela mostra 40 de 125 e o gráfico continua descrevendo 125".
 */
@Import(TestSeeder.class)
class ApplicationSummaryFilterIntegrationTest extends AbstractIntegrationTest {

    /** Duas inscrições a uma hora de distância em UTC, mas em dias locais diferentes. */
    private static final Instant ANTES_DA_MEIA_NOITE = Instant.parse("2026-03-01T02:30:00Z");
    private static final Instant DEPOIS_DA_MEIA_NOITE = Instant.parse("2026-03-01T03:30:00Z");
    private static final Instant MEIO_DIA_01 = Instant.parse("2026-03-01T15:00:00Z");
    private static final Instant MEIO_DIA_02 = Instant.parse("2026-03-02T15:00:00Z");
    private static final Instant TARDE_02 = Instant.parse("2026-03-02T18:00:00Z");

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private String token;
    private UUID processId;
    private UUID computacao;
    private UUID design;

    @BeforeEach
    void setUp() {
        String slug = "ej-filtro-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ Filtro", slug);
        seeder.seedAccount(tenantId, "dono@" + slug + ".dev", "senha123", Standing.OWNER);
        token = login("dono@" + slug + ".dev", "senha123");
        processId = openProcess(token, "PS Filtro");

        computacao = firstCourse();
        design = seeder.seedCourse(tenantId, "Design");

        insert("Ana Alvo", "ana@example.com", computacao, (short) 2, ANTES_DA_MEIA_NOITE, false);
        insert("Bruno Curso", "bruno@example.com", computacao, (short) 4, DEPOIS_DA_MEIA_NOITE, false);
        insert("Carla Periodo", "carla@example.com", design, (short) 6, MEIO_DIA_01, false);
        insert("Diego Nulo", "diego@example.com", design, null, MEIO_DIA_02, false);
        insert("Elisa Fim", "elisa@example.com", computacao, (short) 8, TARDE_02, true);
    }

    @Test
    @DisplayName("cada um dos oito filtros produz o mesmo total na listagem e no resumo")
    void shouldMatchListingTotalForEveryFilter() {
        assertParity("");
        assertParity("?q=Ana");
        assertParity("?course_id=" + computacao);
        assertParity("?min_term=6");
        assertParity("?max_term=4");
        assertParity("?has_cv=true");
        assertParity("?has_cv=false");
        assertParity("?has_links=true");
        assertParity("?has_links=false");
        assertParity("?from=" + instantParam(DEPOIS_DA_MEIA_NOITE));
        assertParity("?to=" + instantParam(DEPOIS_DA_MEIA_NOITE));
    }

    @Test
    @DisplayName("combinações discriminantes também batem, inclusive quando não sobra ninguém")
    void shouldMatchListingTotalForCombinedFilters() {
        assertParity("?course_id=" + computacao + "&max_term=4");
        assertParity("?course_id=" + design + "&min_term=6");
        assertParity("?q=Ana&course_id=" + design);
        assertParity("?from=" + instantParam(DEPOIS_DA_MEIA_NOITE) + "&to=" + instantParam(MEIO_DIA_02));
        assertParity("?course_id=" + computacao + "&min_term=2&max_term=4&has_cv=false");
        assertParity("?has_links=true&course_id=" + design);
    }

    @Test
    @DisplayName("page, size e sort não alteram o resumo")
    void shouldIgnorePagination() {
        JsonNode semPaginacao = summary("");
        JsonNode comPaginacao = summary("?page=1&size=1&sort=email,asc");

        assertThat(comPaginacao).isEqualTo(semPaginacao);
        assertThat(comPaginacao.path("total").path("value").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("as distribuições somam o total e descrevem o mesmo conjunto filtrado")
    void shouldKeepDistributionsConsistentWithTotal() {
        JsonNode resumo = summary("?course_id=" + computacao);
        long total = resumo.path("total").path("value").asLong();

        assertThat(total).isEqualTo(3);
        assertThat(sumOf(resumo.path("by_course"), "count")).isEqualTo(total);
        assertThat(sumOf(resumo.path("by_term"), "count")).isEqualTo(total);
        assertThat(sumOf(resumo.path("by_day"), "value")).isEqualTo(total);
        assertThat(sumOfShares(resumo.path("by_course"))).isCloseTo(1d, within(1e-9));
        assertThat(sumOfShares(resumo.path("by_term"))).isCloseTo(1d, within(1e-9));
    }

    @Test
    @DisplayName("filtrar por curso deixa uma categoria; curso sem correspondência deixa nenhuma")
    void shouldCollapseCourseDistributionToTheFilteredCourse() {
        JsonNode comResultado = summary("?course_id=" + design);
        assertThat(comResultado.path("by_course")).hasSize(1);
        assertThat(comResultado.path("by_course").get(0).path("key").path("id").asText())
                .isEqualTo(design.toString());
        assertThat(comResultado.path("by_course").get(0).path("count").asInt()).isEqualTo(2);
        assertThat(comResultado.path("by_course").get(0).path("share").asDouble()).isEqualTo(1d);

        UUID semInscritos = seeder.seedCourse(tenantId, "Engenharia de Software");
        JsonNode vazio = summary("?course_id=" + semInscritos);
        assertThat(vazio.path("total").path("value").asInt()).isZero();
        assertThat(vazio.path("by_course")).isEmpty();
        assertThat(vazio.path("by_term")).isEmpty();
        assertThat(vazio.path("by_day")).isEmpty();
        assertThat(vazio.path("peak_day").isNull()).isTrue();
        assertThat(vazio.path("last_day_share").isNull()).isTrue();
        assertThat(vazio.path("median_term").isNull()).isTrue();
        assertThat(vazio.path("first_submitted_at").isNull()).isTrue();
    }

    @Test
    @DisplayName("a curva diária agrupa no fuso da EJ: 23h30 local é o dia anterior")
    void shouldGroupByLocalDay() {
        JsonNode byDay = summary("").path("by_day");

        // Ana às 23h30 de 28/02 local, embora seja 01/03 em UTC.
        assertThat(byDay).hasSize(3);
        assertThat(byDay.get(0).path("date").asText()).isEqualTo("2026-02-28");
        assertThat(byDay.get(0).path("value").asInt()).isEqualTo(1);
        assertThat(byDay.get(1).path("date").asText()).isEqualTo("2026-03-01");
        assertThat(byDay.get(1).path("value").asInt()).isEqualTo(2);
        assertThat(byDay.get(2).path("date").asText()).isEqualTo("2026-03-02");
        assertThat(byDay.get(2).path("value").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("from e to são inclusivos e recortam a curva diária nas bordas")
    void shouldRespectInstantBoundaries() {
        JsonNode aPartirDeBruno = summary("?from=" + instantParam(DEPOIS_DA_MEIA_NOITE));
        assertThat(aPartirDeBruno.path("total").path("value").asInt()).isEqualTo(4);
        assertThat(aPartirDeBruno.path("by_day").get(0).path("date").asText()).isEqualTo("2026-03-01");

        JsonNode ateBruno = summary("?to=" + instantParam(DEPOIS_DA_MEIA_NOITE));
        assertThat(ateBruno.path("total").path("value").asInt()).isEqualTo(2);
        assertThat(sumOf(ateBruno.path("by_day"), "value")).isEqualTo(2);

        // Faixa invertida não é erro: simplesmente não seleciona nada.
        JsonNode invertida = summary("?from=" + instantParam(MEIO_DIA_02) + "&to=" + instantParam(ANTES_DA_MEIA_NOITE));
        assertThat(invertida.path("total").path("value").asInt()).isZero();
        assertThat(listing("?from=" + instantParam(MEIO_DIA_02) + "&to=" + instantParam(ANTES_DA_MEIA_NOITE))
                .path("page").path("total_elements").asInt()).isZero();
    }

    @Test
    @DisplayName("os indicadores derivados acompanham o conjunto filtrado")
    void shouldDeriveIndicatorsFromTheFilteredSet() {
        JsonNode todos = summary("");
        assertThat(todos.path("median_term").asInt()).isEqualTo(4);
        assertThat(todos.path("peak_day").path("date").asText()).isEqualTo("2026-03-02");
        assertThat(todos.path("last_day_share").asDouble()).isEqualTo(2d / 5);

        JsonNode veteranos = summary("?min_term=6");
        assertThat(veteranos.path("total").path("value").asInt()).isEqualTo(2);
        assertThat(veteranos.path("median_term").asInt()).isEqualTo(6);
        assertThat(veteranos.path("peak_day").path("date").asText()).isEqualTo("2026-03-02");
        assertThat(veteranos.path("peak_day").path("count").asInt()).isEqualTo(1);
        assertThat(veteranos.path("last_day_share").asDouble()).isEqualTo(1d / 2);

        // Só quem não informou período: a mediana fica indefinida, não zero.
        JsonNode semPeriodo = summary("?q=Diego");
        assertThat(semPeriodo.path("total").path("value").asInt()).isEqualTo(1);
        assertThat(semPeriodo.path("median_term").isNull()).isTrue();
        assertThat(semPeriodo.path("by_term").get(0).path("key").path("id").isNull()).isTrue();
    }

    @Test
    @DisplayName("listagem e resumo rejeitam os mesmos filtros inválidos")
    void shouldRejectTheSameInvalidFilters() {
        for (String query : List.of("?course_id=nao-e-uuid", "?min_term=abc", "?has_cv=talvez",
                "?has_links=talvez", "?from=ontem")) {
            assertThat(getWithToken(applications() + query, token).getStatusCode())
                    .as("listagem %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(getWithToken(applications() + "/summary" + query, token).getStatusCode())
                    .as("resumo %s", query).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("q ignora acento e caixa nos dois endpoints, com o mesmo total")
    void shouldNormalizeSearchIdentically() {
        assertParity("?q=PERIODO");
        assertParity("?q=período");
        assertThat(summary("?q=período").path("total").path("value").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("processo de outra EJ é 404, e o resumo não vaza inscrição alheia")
    void shouldIsolateTenants() {
        String outroSlug = "ej-outra-" + UUID.randomUUID().toString().substring(0, 8);
        UUID outroTenant = seeder.seedTenant("EJ Outra", outroSlug);
        seeder.seedAccount(outroTenant, "dono@" + outroSlug + ".dev", "senha123", Standing.OWNER);
        String outroToken = login("dono@" + outroSlug + ".dev", "senha123");

        assertThat(getWithToken(applications() + "/summary", outroToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getWithToken("/v1/recruitment/processes/" + UUID.randomUUID() + "/applications/summary",
                token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("sem autenticação é 401; sem recruitment:read é 403")
    void shouldRequireAuthorization() {
        assertThat(getWithToken(applications() + "/summary", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken(applications() + "/summary", token).getHeaders().getCacheControl())
                .isEqualTo("private, no-store");

        String slug = "ej-sem-perm-" + UUID.randomUUID().toString().substring(0, 8);
        UUID outroTenant = seeder.seedTenant("EJ Sem Permissao", slug);
        seeder.seedAccount(outroTenant, "membro@" + slug + ".dev", "senha123", Standing.MEMBER);
        String semPermissao = login("membro@" + slug + ".dev", "senha123");

        assertThat(getWithToken(applications() + "/summary", semPermissao).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * O que quebra em {@code READ COMMITTED}: entre a consulta de totais e a de distribuição chega
     * uma inscrição nova, o total a inclui e o agrupamento não — e a resposta se contradiz. Escrever
     * durante a leitura é o único jeito de provar que o isolamento, e não a boa vontade, garante isso.
     */
    @Test
    @DisplayName("com escrita concorrente, as agregações de uma mesma resposta continuam coerentes")
    void shouldReadFromASingleSnapshot() throws Exception {
        AtomicBoolean escrevendo = new AtomicBoolean(true);
        CountDownLatch comecou = new CountDownLatch(1);

        Thread escritor = Thread.ofPlatform().start(() -> {
            comecou.countDown();
            int i = 0;
            while (escrevendo.get())
                insert("Concorrente " + i, "concorrente-" + i++ + "@example.com",
                        computacao, (short) 3, Instant.now(), false);
        });

        try {
            assertThat(comecou.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 40; i++) {
                JsonNode resumo = summary("");
                long total = resumo.path("total").path("value").asLong();
                assertThat(sumOf(resumo.path("by_course"), "count")).as("by_course na leitura %d", i)
                        .isEqualTo(total);
                assertThat(sumOf(resumo.path("by_term"), "count")).as("by_term na leitura %d", i)
                        .isEqualTo(total);
                assertThat(sumOf(resumo.path("by_day"), "value")).as("by_day na leitura %d", i)
                        .isEqualTo(total);
            }
        } finally {
            escrevendo.set(false);
            escritor.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private void assertParity(String query) {
        long naListagem = listing(query).path("page").path("total_elements").asLong();
        long noResumo = summary(query).path("total").path("value").asLong();
        assertThat(noResumo).as("resumo e listagem para %s", query.isEmpty() ? "(sem filtro)" : query)
                .isEqualTo(naListagem);
    }

    private JsonNode listing(String query) {
        return getJson(applications() + query);
    }

    private JsonNode summary(String query) {
        return getJson(applications() + "/summary" + query);
    }

    private String applications() {
        return "/v1/recruitment/processes/" + processId + "/applications";
    }

    /** Sem pré-codificar: o RestTemplate trata a URL como template e codificaria o %XX de novo. */
    private static String instantParam(Instant instant) {
        return instant.toString();
    }

    private static long sumOf(JsonNode distribution, String field) {
        long sum = 0;
        for (JsonNode entry : distribution) sum += entry.path(field).asLong();
        return sum;
    }

    private static double sumOfShares(JsonNode distribution) {
        double sum = 0;
        for (JsonNode entry : distribution) sum += entry.path("share").asDouble();
        return sum;
    }

    /**
     * Inserção direta porque o fixture precisa de {@code created_at} escolhido: a curva diária só
     * pode ser verificada com instantes nas bordas da meia-noite local, e o endpoint público carimba
     * o instante da requisição.
     */
    private void insert(String fullName, String email, UUID courseId, Short term, Instant createdAt,
                        boolean withLink) {
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
                new org.springframework.core.ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
    }

    private UUID openProcess(String token, String title) {
        UUID id = post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + id + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);
        return id;
    }

    private JsonNode getJson(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        String body = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
