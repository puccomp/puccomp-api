package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.recruitment.applications.CandidateApplicationReceiptResponse;
import br.com.puccomp.api.recruitment.applications.SubmitCandidateApplicationRequest;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.PublicProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class CandidateApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubMailTransport() {
        Mockito.reset(mailSender);
        Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("pessoa anônima vê o processo aberto e envia sua inscrição pelo slug da EJ")
    void shouldBrowseAndSubmitThroughPublicSurface() {
        String token = ownerOf("EJ Alpha", "ej-alpha-application", "dono@alpha-application.dev");
        UUID processId = openProcess(token, "Processo Alpha", "Edital A");

        ResponseEntity<List<PublicProcessResponse>> open = get("/v1/public/ej-alpha-application/processes", null,
                new ParameterizedTypeReference<List<PublicProcessResponse>>() { });
        assertThat(open.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(open.getBody()).singleElement()
                .satisfies(process -> assertThat(process.title()).isEqualTo("Processo Alpha"));

        ResponseEntity<CandidateApplicationReceiptResponse> submission = post(
                publicProcess("ej-alpha-application", processId) + "/applications",
                application("joao@example.com"), null, CandidateApplicationReceiptResponse.class);

        assertThat(submission.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submission.getBody().id()).isNotNull();
        assertThat(submission.getBody().submittedAt()).isNotNull();
        assertThat(getWithToken(internalApplications(processId), token).getBody())
                .contains("joao@example.com");
    }

    @Test
    @DisplayName("cada inscrição preserva o snapshot enviado naquele processo")
    void shouldPreserveSubmittedDataAcrossProcesses() {
        String token = ownerOf("EJ Histórico", "ej-historico-application", "dono@historico-application.dev");
        UUID firstProcess = openProcess(token, "PS 2026.1", null);
        UUID secondProcess = openProcess(token, "PS 2026.2", null);
        String email = "ana@example.com";

        post(publicProcess("ej-historico-application", firstProcess) + "/applications",
                new SubmitCandidateApplicationRequest("Ana Original", email, "31999990000",
                        "Computação", "3º período", List.of("https://github.com/ana-original"), true),
                null, CandidateApplicationReceiptResponse.class);
        post(publicProcess("ej-historico-application", secondProcess) + "/applications",
                new SubmitCandidateApplicationRequest("Ana Atual", email, "31988880000",
                        "Computação", "4º período", List.of("https://github.com/ana-atual"), true),
                null, CandidateApplicationReceiptResponse.class);

        String firstApplication = getWithToken(internalApplications(firstProcess), token).getBody();
        assertThat(firstApplication)
                .contains("Ana Original", "31999990000", "https://github.com/ana-original")
                .doesNotContain("Ana Atual", "31988880000", "https://github.com/ana-atual");
    }

    @Test
    @DisplayName("o mesmo e-mail pode se inscrever em processos diferentes")
    void shouldAcceptSameEmailInDifferentProcesses() {
        String token = ownerOf("EJ Recorrente", "ej-recorrente", "dono@recorrente.dev");
        UUID firstProcess = openProcess(token, "PS 1", null);
        UUID secondProcess = openProcess(token, "PS 2", null);

        assertThat(post(publicProcess("ej-recorrente", firstProcess) + "/applications",
                application("recorrente@example.com"), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(publicProcess("ej-recorrente", secondProcess) + "/applications",
                application("recorrente@example.com"), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("processo em DRAFT é indistinguível de inexistente na superfície pública")
    void shouldHideDraftProcessesFromPublicSurface() {
        String token = ownerOf("EJ Rascunho", "ej-rascunho", "dono@rascunho.dev");
        UUID processId = createProcess(token, "Processo ainda não publicado");

        assertThat(get("/v1/public/ej-rascunho/processes", null,
                new ParameterizedTypeReference<List<PublicProcessResponse>>() { }).getBody()).isEmpty();
        assertThat(get(publicProcess("ej-rascunho", processId), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(publicProcess("ej-rascunho", processId) + "/applications",
                application("cedo@example.com"), null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("o slug público define o tenant mesmo quando o chamador está autenticado em outra EJ")
    void shouldBindApplicationToSlugTenant() {
        String tokenA = ownerOf("EJ Alpha", "ej-alpha-bind", "dono@alpha-bind.dev");
        String tokenB = ownerOf("EJ Beta", "ej-beta-bind", "dono@beta-bind.dev");
        UUID processA = openProcess(tokenA, "Processo Alpha", null);

        assertThat(post(publicProcess("ej-alpha-bind", processA) + "/applications",
                application("pessoa@example.com"), tokenB, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(getWithToken(internalApplications(processA), tokenA).getBody())
                .contains("pessoa@example.com");
        assertThat(getWithToken(internalApplications(processA), tokenB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("não aceita inscrição duplicada no mesmo processo ignorando maiúsculas do e-mail")
    void shouldRejectDuplicateApplicationIgnoringCase() {
        String token = ownerOf("EJ Duplicada", "ej-duplicada", "dono@duplicada.dev");
        String path = publicProcess("ej-duplicada", openProcess(token, "Processo", null)) + "/applications";

        assertThat(post(path, application("maria@example.com"), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<ErrorResponse> duplicate = post(path, application("MARIA@example.com"), null,
                ErrorResponse.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().message()).contains("Você já se inscreveu");
        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(MimeMessage.class));
    }

    @Test
    @DisplayName("não aceita inscrição depois que o processo é fechado")
    void shouldRejectApplicationWhenProcessIsClosed() {
        String token = ownerOf("EJ Fechada", "ej-fechada", "dono@fechada.dev");
        UUID processId = openProcess(token, "Processo", null);
        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CLOSED), token, SelectionProcessResponse.class);

        ResponseEntity<ErrorResponse> submission = post(publicProcess("ej-fechada", processId) + "/applications",
                application("tarde@example.com"), null, ErrorResponse.class);

        assertThat(submission.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(submission.getBody().message()).contains("não está aceitando inscrições");
    }

    @Test
    @DisplayName("a inscrição guarda até cinco links na ordem enviada")
    void shouldStoreApplicationLinksInOrder() {
        String token = ownerOf("EJ Links", "ej-links", "dono@links.dev");
        UUID processId = openProcess(token, "PS Links", null);
        List<String> links = List.of("https://linkedin.com/in/ana", "https://github.com/ana",
                "https://behance.net/ana", "https://ana.dev", "https://ana.dev/cv.pdf");

        assertThat(post(publicProcess("ej-links", processId) + "/applications",
                new SubmitCandidateApplicationRequest("Ana Lima", "ana@example.com", "31999990000",
                        "Computação", null, links, true), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(getWithToken(internalApplications(processId), token).getBody())
                .containsSubsequence(links.toArray(String[]::new));
    }

    @Test
    @DisplayName("recusa mais de cinco links e valores que não são URL")
    void shouldRejectInvalidLinks() {
        String token = ownerOf("EJ Links Inválidos", "ej-links-invalidos", "dono@links-invalidos.dev");
        UUID processId = openProcess(token, "PS Links", null);
        String path = publicProcess("ej-links-invalidos", processId) + "/applications";

        assertThat(post(path, new SubmitCandidateApplicationRequest("Excesso", "excesso@example.com", "31999990000",
                "Computação", null, List.of("https://a.dev", "https://b.dev", "https://c.dev",
                        "https://d.dev", "https://e.dev", "https://f.dev"), true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, new SubmitCandidateApplicationRequest("Inválido", "invalido@example.com", "31999990000",
                "Computação", null, List.of("não é uma URL"), true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("período é texto livre e opcional")
    void shouldAcceptFreeFormCurrentTerm() {
        String token = ownerOf("EJ Período", "ej-periodo", "dono@periodo.dev");
        UUID processId = openProcess(token, "PS Período", null);
        String path = publicProcess("ej-periodo", processId) + "/applications";

        assertThat(post(path, new SubmitCandidateApplicationRequest("Formando", "formando@example.com", "31999990000",
                "Engenharia", "formando", null, true), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(path, new SubmitCandidateApplicationRequest("Sem Período", "sem-periodo@example.com",
                "31999990000", "Engenharia", null, null, true), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String ownerOf(String organizationName, String slug, String email) {
        UUID tenantId = seeder.seedTenant(organizationName, slug);
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private UUID createProcess(String token, String title) {
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null), token,
                SelectionProcessResponse.class).getBody().id();
    }

    private UUID openProcess(String token, String title, String description) {
        UUID processId = post("/v1/recruitment/processes", new SelectionProcessRequest(title, description), token,
                SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);
        return processId;
    }

    private static String publicProcess(String orgSlug, UUID processId) {
        return "/v1/public/" + orgSlug + "/processes/" + processId;
    }

    private static String internalApplications(UUID processId) {
        return "/v1/recruitment/processes/" + processId + "/applications";
    }

    private static SubmitCandidateApplicationRequest application(String email) {
        return new SubmitCandidateApplicationRequest("João Silva", email, "31999998888",
                "Sistemas de Informação", "3º período", null, true);
    }
}
