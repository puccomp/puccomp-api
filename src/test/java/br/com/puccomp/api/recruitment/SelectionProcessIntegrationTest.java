package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.organization.CourseCatalog;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.core.ParameterizedTypeReference;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
                "Descrição do processo 2026.1", null, null, null, null, null);

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
                agora.plus(10, ChronoUnit.DAYS), agora.plus(1, ChronoUnit.DAYS), null, null, null),
                token, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/v1/recruitment/processes", new SelectionProcessRequest("Resultado antes", null,
                agora, agora.plus(10, ChronoUnit.DAYS), agora.plus(5, ChronoUnit.DAYS), null, null),
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

    @Test
    @DisplayName("depois do prazo o processo segue visível ao candidato, sem aceitar inscrição")
    void shouldKeepProcessVisibleToCandidatesAfterDeadline() {
        String token = ownerOf("EJ Visível", "ej-visivel", "dono@visivel.dev");
        Instant resultado = Instant.now().plus(20, ChronoUnit.DAYS);
        UUID processId = createProcess(token, "PS Visível", null, Instant.now().plusSeconds(2), resultado);
        open(token, processId);

        JsonNode aberto = publicProcess("ej-visivel", processId);
        assertThat(aberto.path("accepting_applications").asBoolean()).isTrue();
        assertThat(aberto.path("status").asText()).isEqualTo("OPEN");

        await(3);

        // Antes, o candidato que voltasse ao link depois do prazo levava 404.
        ResponseEntity<String> depois = get("/v1/public/ej-visivel/processes/" + processId, null, String.class);
        assertThat(depois.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode vencido = publicProcess("ej-visivel", processId);
        assertThat(vencido.path("accepting_applications").asBoolean()).isFalse();
        assertThat(vencido.path("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(vencido.path("result_at").isNull()).isFalse();

        // Mas sai da vitrine: a listagem pública só mostra quem aceita inscrição.
        assertThat(get("/v1/public/ej-visivel/processes", null, String.class).getBody()).doesNotContain(processId.toString());
    }

    @Test
    @DisplayName("processo encerrado continua legível; DRAFT permanece invisível")
    void shouldExposeClosedButNeverDraft() {
        String token = ownerOf("EJ Encerrado", "ej-encerrado", "dono@encerrado.dev");
        UUID rascunho = createProcess(token, "PS Rascunho", null, null, null);
        UUID encerrado = createProcess(token, "PS Encerrado", null, null, null);
        open(token, encerrado);
        changeStatus(token, encerrado, SelectionProcessStatus.CLOSED);

        assertThat(get("/v1/public/ej-encerrado/processes/" + encerrado, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(publicProcess("ej-encerrado", encerrado).path("accepting_applications").asBoolean()).isFalse();

        assertThat(get("/v1/public/ej-encerrado/processes/" + rascunho, null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("busca processos por título sem diferenciar acento ou caixa e combina com status")
    void shouldSearchProcessesByTitleAndStatus() {
        String token = ownerOf("EJ Busca", "ej-busca", "dono@busca.dev");
        UUID alvo = createProcess(token, "Seleção de Tecnologia");
        createProcess(token, "Processo Comercial");

        // Termo sem acento achando título com acento: quem normaliza é a coluna gerada.
        JsonNode semAcento = listProcesses(token, "?q={q}", "SELECAO");
        assertThat(semAcento.path("content")).hasSize(1);
        assertThat(semAcento.path("content").get(0).path("id").asText()).isEqualTo(alvo.toString());

        // Termo com acento: aqui quem tem que normalizar é o Java, senão não casa com a coluna.
        assertThat(listProcesses(token, "?q={q}", "Seleção").path("content")).hasSize(1);
        assertThat(listProcesses(token, "?q={q}", "TECNOLOGIA").path("content")).hasSize(1);

        assertThat(listProcesses(token, "?q={q}&status=OPEN", "selecao").path("content")).isEmpty();
        assertThat(listProcesses(token, "?q={q}", "s").path("content")).hasSize(2);

        // Curinga digitado vale como texto. Sem escape, "%o" viraria "qualquer coisa + o" e casaria
        // com os dois; "el_ç" viraria "el + um caractere + c", que casa dentro de "seleção".
        assertThat(listProcesses(token, "?q={q}", "%o").path("content")).isEmpty();
        assertThat(listProcesses(token, "?q={q}", "el_ç").path("content")).isEmpty();
    }

    private String ownerOf(String ejName, String slug, String email) {
        UUID tenantId = seeder.seedTenant(ejName, slug);
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private UUID createProcess(String token, String title) {
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null, null, null, null),
                token, SelectionProcessResponse.class).getBody().id();
    }

    private UUID createProcess(String token, String title, Instant opensAt, Instant closesAt, Instant resultAt) {
        return post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, null, opensAt, closesAt, resultAt, null, null),
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
        return listProcesses(token, query, new Object[0]);
    }

    /**
     * O termo vai como variável de URI, não concatenado: assim o Spring o codifica uma vez só.
     * Concatenar já codificado faz o RestTemplate codificar de novo, e o servidor recebe o %XX cru —
     * o que transforma asserção de curinga em teste vazio.
     */
    private JsonNode listProcesses(String token, String query, Object... uriVariables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var body = rest.exchange("/v1/recruitment/processes" + query, HttpMethod.GET,
                new HttpEntity<>(headers), String.class, uriVariables).getBody();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void submitApplication(String slug, UUID processId, String email) {
        UUID courseId = get("/v1/public/" + slug + "/courses", null,
                new ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
        post("/v1/public/" + slug + "/processes/" + processId + "/applications",
                new SubmitCandidateApplicationRequest("Candidato Teste", email, "31999998888",
                        courseId, (short) 3, null, true),
                null, String.class);
    }

    private JsonNode publicProcess(String slug, UUID processId) {
        try {
            return mapper.readTree(get("/v1/public/" + slug + "/processes/" + processId, null, String.class).getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
