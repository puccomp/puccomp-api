package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.applications.SubmitCandidateApplicationRequest;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.exception.ErrorResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class SelectionProcessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("deve criar processo em DRAFT e devolvê-lo na listagem da EJ")
    void shouldCreateAndListSelectionProcesses() {
        String token = ownerOf("EJ Recrutamento", "ej-recrutamento", "dono@recrutamento.dev");

        var request = new SelectionProcessRequest(
                "Processo Seletivo 2026.1",
                "Descrição do processo 2026.1", null, null, null);

        ResponseEntity<SelectionProcessResponse> created =
                post("/v1/recruitment/processes", request, token, SelectionProcessResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().title()).isEqualTo("Processo Seletivo 2026.1");
        assertThat(created.getBody().status()).isEqualTo(SelectionProcessStatus.DRAFT);

        JsonNode list = listProcesses(token, "");
        assertThat(list.path("content")).hasSize(1);
        assertThat(list.path("page").path("total_elements").asInt()).isEqualTo(1);
        assertThat(list.path("content").get(0).has("description")).isFalse();
    }

    @Test
    @DisplayName("deve isolar processos seletivos entre empresas juniores diferentes")
    void shouldIsolateSelectionProcessesBetweenTenants() {
        String tokenA = ownerOf("EJ Alpha", "ej-alpha", "dono@alpha.dev");
        String tokenB = ownerOf("EJ Beta", "ej-beta", "dono@beta.dev");

        UUID processId = createProcess(tokenA, "Processo Alpha");

        assertThat(listProcesses(tokenB, "").path("content")).isEmpty();

        ResponseEntity<String> readB = getWithToken("/v1/recruitment/processes/" + processId, tokenB);
        assertThat(readB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deve recusar transição de status inválida")
    void shouldRejectInvalidStatusTransition() {
        String token = ownerOf("EJ Transicao", "ej-transicao", "dono@transicao.dev");
        UUID processId = createProcess(token, "Processo");

        ResponseEntity<ErrorResponse> jump = patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CLOSED), token, ErrorResponse.class);
        assertThat(jump.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jump.getBody().message()).contains("Não é possível mudar o processo de DRAFT para CLOSED");

        assertThat(patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CANCELLED), token, SelectionProcessResponse.class)
                .getBody().status()).isEqualTo(SelectionProcessStatus.CANCELLED);

        ResponseEntity<ErrorResponse> reopen = patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, ErrorResponse.class);
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("passado o prazo, o processo aparece como IN_REVIEW sem ninguém ter mexido nele")
    void shouldDeriveInReviewOncePastDeadline() {
        String token = ownerOf("EJ Prazo", "ej-prazo", "dono@prazo.dev");
        Instant abriu = Instant.now().minus(10, ChronoUnit.DAYS);
        UUID processId = createProcess(token, "PS Prazo", abriu, Instant.now().plusSeconds(2), null);
        open(token, processId);

        SelectionProcessResponse aberto = read(token, processId);
        assertThat(aberto.status()).isEqualTo(SelectionProcessStatus.OPEN);
        assertThat(aberto.acceptingApplications()).isTrue();

        await(3);

        SelectionProcessResponse vencido = read(token, processId);
        assertThat(vencido.status()).isEqualTo(SelectionProcessStatus.IN_REVIEW);
        assertThat(vencido.acceptingApplications()).isFalse();
    }

    @Test
    @DisplayName("o processo ainda não aceita inscrição antes da data de abertura")
    void shouldNotAcceptBeforeOpeningDate() {
        String token = ownerOf("EJ Agendada", "ej-agendada", "dono@agendada.dev");
        UUID processId = createProcess(token, "PS Agendado",
                Instant.now().plus(2, ChronoUnit.DAYS), Instant.now().plus(9, ChronoUnit.DAYS), null);
        open(token, processId);

        SelectionProcessResponse response = read(token, processId);
        assertThat(response.status()).isEqualTo(SelectionProcessStatus.OPEN);
        assertThat(response.acceptingApplications()).isFalse();
    }

    @Test
    @DisplayName("IN_REVIEW também é alcançável à mão, para encerrar as inscrições antes do prazo")
    void shouldAllowClosingApplicationsAheadOfDeadline() {
        String token = ownerOf("EJ Antecipada", "ej-antecipada", "dono@antecipada.dev");
        UUID processId = createProcess(token, "PS Antecipado", null,
                Instant.now().plus(30, ChronoUnit.DAYS), null);
        open(token, processId);

        assertThat(changeStatus(token, processId, SelectionProcessStatus.IN_REVIEW).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        SelectionProcessResponse response = read(token, processId);
        assertThat(response.status()).isEqualTo(SelectionProcessStatus.IN_REVIEW);
        assertThat(response.acceptingApplications()).isFalse();
    }

    @Test
    @DisplayName("de IN_REVIEW não dá para reabrir as inscrições")
    void shouldNotReopenFromInReview() {
        String token = ownerOf("EJ Sem Volta", "ej-sem-volta", "dono@sem-volta.dev");
        UUID processId = createProcess(token, "PS Sem Volta", null, null, null);
        open(token, processId);
        changeStatus(token, processId, SelectionProcessStatus.IN_REVIEW);

        assertThat(changeStatus(token, processId, SelectionProcessStatus.OPEN).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(changeStatus(token, processId, SelectionProcessStatus.CLOSED).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("recusa janela invertida e resultado anterior ao fim das inscrições")
    void shouldRejectInconsistentWindow() {
        String token = ownerOf("EJ Datas", "ej-datas", "dono@datas.dev");
        Instant agora = Instant.now();

        assertThat(post("/v1/recruitment/processes", new SelectionProcessRequest("Invertido", null,
                agora.plus(10, ChronoUnit.DAYS), agora.plus(1, ChronoUnit.DAYS), null),
                token, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/v1/recruitment/processes", new SelectionProcessRequest("Resultado antes", null,
                agora, agora.plus(10, ChronoUnit.DAYS), agora.plus(5, ChronoUnit.DAYS)),
                token, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a listagem traz a contagem de inscrições e a data da última")
    void shouldExposeApplicationMetrics() throws Exception {
        UUID tenantId = seeder.seedTenant("EJ Métricas", "ej-metricas");
        seeder.seedAccount(tenantId, "dono@metricas.dev", "senha123", Standing.OWNER);
        String token = login("dono@metricas.dev", "senha123");
        UUID processId = createProcess(token, "PS Métricas", null, null, null);
        open(token, processId);

        submitApplication("ej-metricas", processId, "um@example.com");
        submitApplication("ej-metricas", processId, "dois@example.com");

        JsonNode linha = listProcesses(token, "").path("content").get(0);
        assertThat(linha.path("application_count").asInt()).isEqualTo(2);
        assertThat(linha.path("last_application_at").isNull()).isFalse();

        JsonNode detalhe = mapper.readTree(
                getWithToken("/v1/recruitment/processes/" + processId, token).getBody());
        assertThat(detalhe.path("application_count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("processo sem inscrição nenhuma volta com contagem zero, não com campo ausente")
    void shouldReportZeroForProcessWithoutApplications() {
        String token = ownerOf("EJ Vazia", "ej-vazia", "dono@vazia.dev");
        createProcess(token, "PS Vazio", null, null, null);

        JsonNode linha = listProcesses(token, "").path("content").get(0);
        assertThat(linha.path("application_count").asInt()).isZero();
        assertThat(linha.path("last_application_at").isNull()).isTrue();
    }

    @Test
    @DisplayName("o filtro status casa com o status efetivo, não com o gravado")
    void shouldFilterByEffectiveStatus() {
        String token = ownerOf("EJ Filtro", "ej-filtro", "dono@filtro.dev");
        UUID vencido = createProcess(token, "PS Vencido", null, Instant.now().plusSeconds(2), null);
        open(token, vencido);
        UUID vigente = createProcess(token, "PS Vigente", null,
                Instant.now().plus(30, ChronoUnit.DAYS), null);
        open(token, vigente);

        await(3);

        JsonNode abertos = listProcesses(token, "?status=OPEN");
        assertThat(abertos.path("content")).hasSize(1);
        assertThat(abertos.path("content").get(0).path("id").asText()).isEqualTo(vigente.toString());

        JsonNode emAvaliacao = listProcesses(token, "?status=IN_REVIEW");
        assertThat(emAvaliacao.path("content")).hasSize(1);
        assertThat(emAvaliacao.path("content").get(0).path("id").asText()).isEqualTo(vencido.toString());
    }

    private String ownerOf(String ejName, String slug, String email) {
        UUID tenantId = seeder.seedTenant(ejName, slug);
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private UUID createProcess(String token, String title) {
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null, null),
                token, SelectionProcessResponse.class).getBody().id();
    }

    private UUID createProcess(String token, String title, Instant opensAt, Instant closesAt, Instant resultAt) {
        return post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, null, opensAt, closesAt, resultAt),
                token, SelectionProcessResponse.class).getBody().id();
    }

    private void open(String token, UUID processId) {
        changeStatus(token, processId, SelectionProcessStatus.OPEN);
    }

    /** String como tipo de resposta: a transição inválida devolve ErrorResponse, não o processo. */
    private ResponseEntity<String> changeStatus(String token, UUID processId, SelectionProcessStatus status) {
        return patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(status), token, String.class);
    }

    private SelectionProcessResponse read(String token, UUID processId) {
        return get("/v1/recruitment/processes/" + processId, token, SelectionProcessResponse.class).getBody();
    }

    private static void await(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode listProcesses(String token, String query) {
        try {
            return mapper.readTree(getWithToken("/v1/recruitment/processes" + query, token).getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void submitApplication(String slug, UUID processId, String email) {
        post("/v1/public/" + slug + "/processes/" + processId + "/applications",
                new SubmitCandidateApplicationRequest("Candidato Teste", email, "31999998888",
                        "Sistemas de Informação", "3º período", null, true),
                null, String.class);
    }
}
