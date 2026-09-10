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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O relatório histórico não pode ser conferido com dados de "agora": ele é todo definido por bordas
 * de mês, semestre e ano. Aqui o relógio é fixo e avançado de propósito, e cada número tem um valor
 * esperado calculado à mão — é o único jeito de uma virada de mês errada quebrar um teste.
 */
@Import(TestSeeder.class)
class MemberHistoryIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId EJ = ZoneId.of("America/Sao_Paulo");
    private static final AtomicReference<Instant> NOW =
            new AtomicReference<>(local("2026-09-07T12:00:00"));

    /** Relógio substituível: sem ele, "virada de mês" só seria testável esperando ela acontecer. */
    @TestBean(name = "clock")
    private Clock clock;

    static Clock clock() {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return NOW.get(); }
        };
    }

    @Autowired
    private TestSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    /** Sem isto o próprio teste perderia a escala: Jackson leria 1.000000 como o double 1.0, e a
     *  precisão contratada — seis casas em razões, duas em meses — passaria despercebida. */
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private String slug;
    private UUID tenantId;
    private String token;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void setUp() {
        NOW.set(local("2026-02-01T00:00:00"));
        slug = "ej-historico-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = seeder.seedTenant("EJ Historico", slug);
        seeder.seedAccount(tenantId, "dono@" + slug + ".dev", "senha123", Standing.OWNER);

        at("2026-06-10T00:00:00");
        ana = seedMember("ana");
        at("2026-07-05T00:00:00");
        bruno = seedMember("bruno");

        NOW.set(local("2026-09-07T12:00:00"));
        token = login("dono@" + slug + ".dev", "senha123");

        at("2026-07-20T00:00:00");
        retire(ana);
        at("2026-08-10T00:00:00");
        reactivate(ana);
        at("2026-08-15T00:00:00");
        seedMember("carla");
        at("2026-08-20T00:00:00");
        retire(bruno);

        NOW.set(local("2026-09-07T12:00:00"));
    }

    @Test
    @DisplayName("a janela padrão é o último mês civil completo")
    void shouldDefaultToTheLastCompleteMonth() {
        JsonNode relatorio = history("");

        assertThat(relatorio.path("period").path("from").asText()).isEqualTo(iso("2026-08-01T00:00:00"));
        assertThat(relatorio.path("period").path("to").asText()).isEqualTo(iso("2026-09-01T00:00:00"));
        assertThat(relatorio.path("period").path("months").asInt()).isEqualTo(1);
        assertThat(relatorio.path("timezone").asText()).isEqualTo("America/Sao_Paulo");
        assertThat(relatorio.path("scope").asText()).isEqualTo("TENANT");

        // Mesma quantidade de meses civis, terminando onde a atual começa — julho tem 31 dias e
        // agosto também, mas a regra é de meses, não de dias.
        assertThat(relatorio.path("previous_period").path("from").asText())
                .isEqualTo(iso("2026-07-01T00:00:00"));
        assertThat(relatorio.path("previous_period").path("to").asText())
                .isEqualTo(iso("2026-08-01T00:00:00"));
    }

    @Test
    @DisplayName("entradas, reativações e quadro mensal saem do histórico, mês a mês")
    void shouldReportMonthlySeries() {
        JsonNode relatorio = history("?from=2026-06&to=2026-09");

        assertThat(dates(relatorio.path("joins_by_month")))
                .containsExactly("2026-06-01", "2026-07-01", "2026-08-01");
        assertThat(values(relatorio.path("joins_by_month"))).containsExactly(1L, 1L, 1L);

        // Ana voltou em agosto: é reativação, não uma segunda admissão.
        assertThat(values(relatorio.path("reactivations_by_month"))).containsExactly(0L, 0L, 1L);

        // Quadro imediatamente antes do limite exclusivo de cada mês. Em julho Ana já saiu e Bruno
        // entrou; em agosto Bruno saiu, Ana voltou e Carla entrou.
        assertThat(values(relatorio.path("headcount_by_month"))).containsExactly(2L, 2L, 3L);

        assertThat(relatorio.path("active_headcount").path("value").asInt()).isEqualTo(3);
        assertThat(relatorio.path("active_headcount").path("previous").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("turnover usa o quadro médio ponderado pelo tempo, com resultado conhecido")
    void shouldComputeTurnoverAgainstTimeWeightedHeadcount() {
        JsonNode relatorio = history("?from=2026-06&to=2026-09");

        // Janela de 92 dias. Tempo ativo: dono 92, Ana 40 + 22, Bruno 46, Carla 17 = 217 dias.
        // Quadro médio 217/92; duas saídas (Ana em 20/07, Bruno em 20/08): 2 / (217/92) = 184/217.
        assertThat(relatorio.path("turnover").path("value").decimalValue())
                .isEqualByComparingTo("0.847926");
        // Janela anterior coberta e sem saídas: zero é resultado conhecido, não ausência.
        assertThat(relatorio.path("turnover").path("previous").decimalValue())
                .isEqualByComparingTo("0.000000");
        // As seis casas são contrato, e o JSON precisa carregá-las mesmo quando são zeros.
        assertThat(rawHistory("?from=2026-06&to=2026-09"))
                .contains("\"turnover\":{\"value\":0.847926,\"previous\":0.000000}");
    }

    @Test
    @DisplayName("permanência média usa os intervalos encerrados na janela, em meses de 365,2425/12 dias")
    void shouldComputeAverageTenure() {
        JsonNode relatorio = history("?from=2026-06&to=2026-09");

        // Ana ficou 40 dias e Bruno 46: média de 43 dias, sobre um mês de 30,436875 dias.
        assertThat(relatorio.path("average_tenure_months").path("value").decimalValue())
                .isEqualByComparingTo("1.41");
        assertThat(rawHistory("?from=2026-06&to=2026-09")).contains("\"average_tenure_months\":{\"value\":1.41");
        // Nada encerrou na janela anterior: sem amostra, a média é indefinida — não zero.
        assertThat(relatorio.path("average_tenure_months").path("previous").isNull()).isTrue();
    }

    @Test
    @DisplayName("coortes agrupam por semestre civil e marcam o recorte parcial")
    void shouldGroupCohortsBySemester() {
        JsonNode cohorts = history("?from=2026-06&to=2026-09").path("cohorts");

        assertThat(cohorts).hasSize(2);
        assertThat(cohorts.get(0).path("period").asText()).isEqualTo("2026.1");
        assertThat(cohorts.get(0).path("joined").asInt()).isEqualTo(1);
        assertThat(cohorts.get(0).path("still_active").asInt()).isEqualTo(1);
        assertThat(cohorts.get(0).path("retention").decimalValue()).isEqualByComparingTo("1.000000");
        // A janela começa em junho e o semestre em janeiro: a coorte é um pedaço do semestre.
        assertThat(cohorts.get(0).path("partial_period").asBoolean()).isTrue();

        assertThat(cohorts.get(1).path("period").asText()).isEqualTo("2026.2");
        assertThat(cohorts.get(1).path("joined").asInt()).isEqualTo(2);
        assertThat(cohorts.get(1).path("still_active").asInt()).isEqualTo(1);
        assertThat(cohorts.get(1).path("retention").decimalValue()).isEqualByComparingTo("0.500000");
        assertThat(rawHistory("?from=2026-06&to=2026-09")).contains("\"retention\":1.000000");
        assertThat(cohorts.get(1).path("partial_period").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("sair e voltar preserva a saída no mês original e reconstitui o período afastado")
    void shouldPreserveExitsAcrossReactivation() {
        JsonNode relatorio = history("?from=2026-07&to=2026-08");

        // A saída de Ana em julho continua contada, mesmo ela tendo voltado em agosto.
        assertThat(relatorio.path("turnover").path("value").asDouble()).isGreaterThan(0);
        // E em julho, entre a saída e a volta, ela não conta no quadro.
        assertThat(values(relatorio.path("headcount_by_month"))).containsExactly(2L);
        assertThat(values(relatorio.path("reactivations_by_month"))).containsExactly(0L);
    }

    @Test
    @DisplayName("eventos posteriores à janela não alteram um período já encerrado")
    void shouldNotLetLaterEventsChangeAClosedPeriod() {
        JsonNode antes = history("?from=2026-06&to=2026-09");

        at("2026-09-15T00:00:00");
        reactivate(bruno);
        UUID novo = seedMember("novo");
        retire(novo);
        NOW.set(local("2026-10-02T12:00:00"));

        assertThat(history("?from=2026-06&to=2026-09")).isEqualTo(antes);
    }

    @Test
    @DisplayName("repetir o mesmo status não duplica a saída nem move a sua data")
    void shouldTreatRepeatedStatusAsNoOp() {
        String agostoAntes = rawHistory("?from=2026-08&to=2026-09");

        // Bruno já saiu em 20/08. Aposentá-lo de novo em setembro não pode virar uma segunda saída.
        at("2026-09-10T00:00:00");
        retire(bruno);
        retire(bruno);
        NOW.set(local("2026-10-02T12:00:00"));

        assertThat(eventsOf(bruno)).hasSize(2);
        assertThat(rawHistory("?from=2026-08&to=2026-09")).isEqualTo(agostoAntes);
        assertThat(history("?from=2026-09&to=2026-10").path("turnover").path("value").decimalValue())
                .isEqualByComparingTo("0.000000");
        assertThat(values(history("?from=2026-09&to=2026-10").path("headcount_by_month")))
                .containsExactly(3L);
    }

    @Test
    @DisplayName("trocar cargo não escreve no histórico nem move datas de vínculo")
    void shouldNotRecordAssignmentChanges() {
        int antes = eventsOf(ana).size();
        UUID cargo = seeder.seedCargo(tenantId, "Cargo " + UUID.randomUUID().toString().substring(0, 6));

        at("2026-09-08T00:00:00");
        put("/v1/members/" + ana + "/assignment", Map.of("role_id", cargo.toString()), token, String.class);

        assertThat(eventsOf(ana)).hasSize(antes);
    }

    @Test
    @DisplayName("aposentadorias concorrentes do mesmo membro produzem uma saída só")
    void shouldSerializeConcurrentTransitions() throws Exception {
        at("2026-09-10T00:00:00");
        CountDownLatch largada = new CountDownLatch(1);

        List<Thread> corridas = List.of(
                Thread.ofPlatform().unstarted(() -> race(largada, ana)),
                Thread.ofPlatform().unstarted(() -> race(largada, ana)),
                Thread.ofPlatform().unstarted(() -> race(largada, ana)));
        corridas.forEach(Thread::start);
        largada.countDown();
        for (Thread corrida : corridas) corrida.join(TimeUnit.SECONDS.toMillis(20));

        // Ana já tinha CREATED, a saída de julho e a volta de agosto: as três corridas acrescentam
        // exatamente um evento, e as sequências continuam únicas e sem buracos.
        List<Map<String, Object>> eventos = eventsOf(ana);
        assertThat(eventos).hasSize(4);
        assertThat(eventos.getLast()).containsEntry("to_status", "ALUMNUS");
        assertThat(eventos.stream().map(e -> e.get("sequence")).toList())
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("membro de baseline não ganha coorte nem data de entrada inventada")
    void shouldKeepBaselineMembersUnknown() {
        String legadoSlug = "ej-legado-" + UUID.randomUUID().toString().substring(0, 8);
        NOW.set(local("2026-02-01T00:00:00"));
        UUID legadoTenant = seeder.seedTenant("EJ Legado", legadoSlug);
        seeder.seedAccount(legadoTenant, "dono@" + legadoSlug + ".dev", "senha123", Standing.OWNER);
        NOW.set(local("2026-09-07T12:00:00"));
        String legadoToken = login("dono@" + legadoSlug + ".dev", "senha123");

        // O que a migration produz: o estado observado no marco, sem entrada nem saída.
        UUID legado = baselineMember(legadoTenant, "Legado Ativo", MemberStatus.ACTIVE,
                local("2026-02-01T00:00:00"));
        baselineMember(legadoTenant, "Legado Alumni", MemberStatus.ALUMNUS, local("2026-02-01T00:00:00"));

        at("2026-07-10T00:00:00");
        retire(legadoToken, legado);
        NOW.set(local("2026-09-07T12:00:00"));

        JsonNode relatorio = history(legadoToken, "?from=2026-06&to=2026-09");

        // Nunca houve admissão conhecida: nada de coorte, e nada em joins.
        assertThat(relatorio.path("cohorts")).isEmpty();
        assertThat(values(relatorio.path("joins_by_month"))).containsExactly(0L, 0L, 0L);
        // Dois membros legados, e o alumnus de baseline não virou saída da migration.
        assertThat(relatorio.path("coverage").path("unknown_join_dates").asInt()).isEqualTo(2);
        // A saída de julho conta no turnover, mas o intervalo não tem início conhecido:
        // fica fora da permanência, e a exclusão é informada.
        assertThat(relatorio.path("turnover").path("value").asDouble()).isGreaterThan(0);
        assertThat(relatorio.path("average_tenure_months").path("value").isNull()).isTrue();
        assertThat(relatorio.path("coverage").path("excluded_tenure_intervals").path("value").asInt())
                .isEqualTo(1);

        // Reativar não recupera a data de entrada original: ele continua desconhecido.
        at("2026-08-05T00:00:00");
        reactivate(legadoToken, legado);
        NOW.set(local("2026-09-07T12:00:00"));

        JsonNode depois = history(legadoToken, "?from=2026-06&to=2026-09");
        assertThat(values(depois.path("reactivations_by_month"))).containsExactly(0L, 0L, 1L);
        assertThat(values(depois.path("joins_by_month"))).containsExactly(0L, 0L, 0L);
        assertThat(depois.path("coverage").path("unknown_join_dates").asInt()).isEqualTo(2);
        assertThat(depois.path("cohorts")).isEmpty();
    }

    @Test
    @DisplayName("janela fora da cobertura devolve nulos, e mês descoberto não vira zero")
    void shouldReportPartialCoverageAsNull() {
        String novoSlug = "ej-nova-" + UUID.randomUUID().toString().substring(0, 8);
        NOW.set(local("2026-05-01T00:00:00"));
        UUID novoTenant = seeder.seedTenant("EJ Nova", novoSlug);
        seeder.seedAccount(novoTenant, "dono@" + novoSlug + ".dev", "senha123", Standing.OWNER);
        NOW.set(local("2026-09-07T12:00:00"));
        String novoToken = login("dono@" + novoSlug + ".dev", "senha123");

        JsonNode relatorio = history(novoToken, "?from=2026-03&to=2026-06");

        assertThat(relatorio.path("coverage").path("tracked_since").asText())
                .isEqualTo(iso("2026-05-01T00:00:00"));
        assertThat(relatorio.path("coverage").path("period_complete").asBoolean()).isFalse();
        assertThat(relatorio.path("coverage").path("unknown_join_dates").isNull()).isTrue();

        assertThat(relatorio.path("active_headcount").path("value").isNull()).isTrue();
        assertThat(relatorio.path("turnover").path("value").isNull()).isTrue();
        assertThat(relatorio.path("average_tenure_months").path("value").isNull()).isTrue();
        assertThat(relatorio.path("cohorts").isNull()).isTrue();

        // Março e abril estão fora da cobertura: null, nunca zero presumido. Maio está coberto.
        assertThat(dates(relatorio.path("headcount_by_month")))
                .containsExactly("2026-03-01", "2026-04-01", "2026-05-01");
        assertThat(relatorio.path("headcount_by_month").get(0).path("value").isNull()).isTrue();
        assertThat(relatorio.path("headcount_by_month").get(1).path("value").isNull()).isTrue();
        assertThat(relatorio.path("headcount_by_month").get(2).path("value").asInt()).isEqualTo(1);

        // A janela anterior sem cobertura zera só o previous; a atual, coberta, continua valendo.
        JsonNode coberta = history(novoToken, "?from=2026-06&to=2026-09");
        assertThat(coberta.path("coverage").path("period_complete").asBoolean()).isTrue();
        assertThat(coberta.path("coverage").path("previous_period_complete").asBoolean()).isFalse();
        assertThat(coberta.path("active_headcount").path("value").asInt()).isEqualTo(1);
        assertThat(coberta.path("active_headcount").path("previous").isNull()).isTrue();
        assertThat(coberta.path("coverage").path("excluded_tenure_intervals").path("previous").isNull())
                .isTrue();
    }

    @Test
    @DisplayName("janela inválida é 400 nos limites, no formato e no tamanho")
    void shouldRejectInvalidWindows() {
        for (String query : List.of(
                "?from=2026-06",
                "?to=2026-09",
                "?from=junho&to=2026-09",
                "?from=2026-06&to=2026-6",
                "?from=2026-09&to=2026-09",
                "?from=2026-09&to=2026-08",
                "?from=2024-08&to=2026-09",
                "?from=2026-09&to=2026-10",
                "?from=2026-10&to=2026-11"))
            assertThat(getWithToken("/v1/members/history" + query, token).getStatusCode())
                    .as(query).isEqualTo(HttpStatus.BAD_REQUEST);

        // to pode ser o primeiro mês ainda não concluído: o início dele é o limite exclusivo.
        assertThat(getWithToken("/v1/members/history?from=2026-08&to=2026-09", token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // Exatamente 24 meses passa; 25 não.
        assertThat(getWithToken("/v1/members/history?from=2024-09&to=2026-09", token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("filtros de estado atual são recusados, não ignorados")
    void shouldRejectCurrentStateFilters() {
        for (String query : List.of("?status=ACTIVE", "?role_id=" + UUID.randomUUID(),
                "?department_id=" + UUID.randomUUID(), "?departmentId=" + UUID.randomUUID(),
                "?course_id=" + UUID.randomUUID(), "?standing=MEMBER",
                "?has_role=true", "?has_department=false"))
            assertThat(getWithToken("/v1/members/history" + query, token).getStatusCode())
                    .as(query).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("uma EJ não enxerga o histórico da outra; sem autenticação é 401 e sem permissão, 403")
    void shouldIsolateAndAuthorize() {
        String outroSlug = "ej-outra-hist-" + UUID.randomUUID().toString().substring(0, 8);
        NOW.set(local("2026-02-01T00:00:00"));
        UUID outroTenant = seeder.seedTenant("EJ Outra Hist", outroSlug);
        seeder.seedAccount(outroTenant, "dono@" + outroSlug + ".dev", "senha123", Standing.OWNER);
        NOW.set(local("2026-09-07T12:00:00"));
        String outroToken = login("dono@" + outroSlug + ".dev", "senha123");

        JsonNode vizinha = history(outroToken, "?from=2026-06&to=2026-09");
        assertThat(values(vizinha.path("joins_by_month"))).containsExactly(0L, 0L, 0L);
        assertThat(vizinha.path("active_headcount").path("value").asInt()).isEqualTo(1);
        assertThat(vizinha.path("cohorts")).isEmpty();

        assertThat(getWithToken("/v1/members/history", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithToken("/v1/members/history", token).getHeaders().getCacheControl())
                .isEqualTo("private, no-store");

        UUID cargo = seeder.seedCargo(tenantId, "Sem Leitura " + UUID.randomUUID().toString().substring(0, 6));
        put("/v1/roles/" + cargo + "/permissions", Map.of("permissions", List.of("roles:read")),
                token, String.class);
        String semLeitura = "sem-leitura-" + UUID.randomUUID().toString().substring(0, 8) + "@" + slug + ".dev";
        seeder.seedAccount(tenantId, semLeitura, "senha123", Standing.MEMBER, cargo);
        assertThat(getWithToken("/v1/members/history", login(semLeitura, "senha123")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void race(CountDownLatch largada, UUID memberId) {
        try {
            largada.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        retire(memberId);
    }

    private void at(String localDateTime) {
        NOW.set(local(localDateTime));
    }

    private static Instant local(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(EJ).toInstant();
    }

    private static String iso(String localDateTime) {
        return local(localDateTime).toString();
    }

    private UUID seedMember(String name) {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@" + slug + ".dev";
        return seeder.seedAccount(tenantId, email, "senha123", Standing.MEMBER);
    }

    /** Reproduz o que a migration grava para quem já existia: observação, não entrada. */
    private UUID baselineMember(UUID tenant, String name, MemberStatus status, Instant trackedSince) {
        UUID courseId = jdbc.queryForObject(
                "select id from courses where tenant_id = ? limit 1", UUID.class, tenant);
        UUID memberId = UUID.randomUUID();
        jdbc.update("""
                insert into members (id, tenant_id, name, standing, status, course_id)
                values (?, ?, ?, 'MEMBER', ?, ?)
                """, memberId, tenant, name, status.name(), courseId);
        jdbc.update("""
                insert into member_status_history
                    (id, tenant_id, member_id, sequence, kind, from_status, to_status, occurred_at)
                values (?, ?, ?, 1, 'BASELINE', null, ?, ?)
                """, UUID.randomUUID(), tenant, memberId, status.name(), Timestamp.from(trackedSince));
        return memberId;
    }

    private List<Map<String, Object>> eventsOf(UUID memberId) {
        return jdbc.queryForList(
                "select sequence, kind, to_status from member_status_history where member_id = ? "
                        + "order by sequence", memberId);
    }

    private void retire(UUID memberId) {
        retire(token, memberId);
    }

    private void retire(String bearer, UUID memberId) {
        post("/v1/members/" + memberId + "/retire", null, bearer, String.class);
    }

    private void reactivate(UUID memberId) {
        reactivate(token, memberId);
    }

    private void reactivate(String bearer, UUID memberId) {
        post("/v1/members/" + memberId + "/reactivate", null, bearer, String.class);
    }

    private String rawHistory(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange("/v1/members/history" + query, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
    }

    private JsonNode history(String query) {
        return history(token, query);
    }

    private JsonNode history(String bearer, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        String body = rest.exchange("/v1/members/history" + query, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> dates(JsonNode series) {
        return series.findValuesAsText("date");
    }

    private static List<Long> values(JsonNode series) {
        return series.valueStream().map(point -> point.path("value").asLong()).toList();
    }
}
