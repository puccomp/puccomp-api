package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.organization.CourseCatalog;
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
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class CandidateApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    @MockitoBean
    private JavaMailSender mailSender;

    /** Entrega o e-mail na própria thread da request: sem isso, o {@code @Async} corre com o verify. */
    @TestBean(name = "mailTaskExecutor")
    private TaskExecutor mailTaskExecutor;

    static TaskExecutor mailTaskExecutor() {
        return new SyncTaskExecutor();
    }

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
                application("joao@example.com", courseOf("ej-alpha-application")), null, CandidateApplicationReceiptResponse.class);

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
                        courseOf("ej-historico-application"), (short) 3,
                        List.of("https://github.com/ana-original"), true),
                null, CandidateApplicationReceiptResponse.class);
        post(publicProcess("ej-historico-application", secondProcess) + "/applications",
                new SubmitCandidateApplicationRequest("Ana Atual", email, "31988880000",
                        courseOf("ej-historico-application"), (short) 4,
                        List.of("https://github.com/ana-atual"), true),
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
                application("recorrente@example.com", courseOf("ej-recorrente")), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(publicProcess("ej-recorrente", secondProcess) + "/applications",
                application("recorrente@example.com", courseOf("ej-recorrente")), null, CandidateApplicationReceiptResponse.class)
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
                application("cedo@example.com", courseOf("ej-rascunho")), null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("o slug público define o tenant mesmo quando o chamador está autenticado em outra EJ")
    void shouldBindApplicationToSlugTenant() {
        String tokenA = ownerOf("EJ Alpha", "ej-alpha-bind", "dono@alpha-bind.dev");
        String tokenB = ownerOf("EJ Beta", "ej-beta-bind", "dono@beta-bind.dev");
        UUID processA = openProcess(tokenA, "Processo Alpha", null);

        assertThat(post(publicProcess("ej-alpha-bind", processA) + "/applications",
                application("pessoa@example.com", courseOf("ej-alpha-bind")), tokenB, CandidateApplicationReceiptResponse.class)
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

        assertThat(post(path, application("maria@example.com", courseOf("ej-duplicada")), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<ErrorResponse> duplicate = post(path, application("MARIA@example.com", courseOf("ej-duplicada")), null,
                ErrorResponse.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().message()).contains("Você já se inscreveu");
        Mockito.verify(mailSender, Mockito.times(2)).send(Mockito.any(MimeMessage.class));
    }

    @Test
    @DisplayName("não aceita inscrição depois que o processo é fechado")
    void shouldRejectApplicationWhenProcessIsClosed() {
        String token = ownerOf("EJ Fechada", "ej-fechada", "dono@fechada.dev");
        UUID processId = openProcess(token, "Processo", null);
        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.CLOSED), token, SelectionProcessResponse.class);

        ResponseEntity<ErrorResponse> submission = post(publicProcess("ej-fechada", processId) + "/applications",
                application("tarde@example.com", courseOf("ej-fechada")), null, ErrorResponse.class);

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
                        courseOf("ej-links"), null, links, true), null,
                CandidateApplicationReceiptResponse.class)
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

        UUID courseId = courseOf("ej-links-invalidos");

        assertThat(post(path, new SubmitCandidateApplicationRequest("Excesso", "excesso@example.com", "31999990000",
                courseId, null, List.of("https://a.dev", "https://b.dev", "https://c.dev",
                        "https://d.dev", "https://e.dev", "https://f.dev"), true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, new SubmitCandidateApplicationRequest("Inválido", "invalido@example.com", "31999990000",
                courseId, null, List.of("não é uma URL"), true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("período é número de 1 a 12 e continua opcional")
    void shouldAcceptNumericCurrentTerm() {
        String token = ownerOf("EJ Período", "ej-periodo", "dono@periodo.dev");
        UUID processId = openProcess(token, "PS Período", null);
        String path = publicProcess("ej-periodo", processId) + "/applications";
        UUID courseId = courseOf("ej-periodo");

        assertThat(post(path, new SubmitCandidateApplicationRequest("Oitavo", "oitavo@example.com", "31999990000",
                courseId, (short) 8, null, true), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(path, new SubmitCandidateApplicationRequest("Sem Período", "sem-periodo@example.com",
                "31999990000", courseId, null, null, true), null, CandidateApplicationReceiptResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(post(path, new SubmitCandidateApplicationRequest("Fora da faixa", "fora@example.com",
                "31999990000", courseId, (short) 13, null, true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, new SubmitCandidateApplicationRequest("Zero", "zero@example.com",
                "31999990000", courseId, (short) 0, null, true), null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("curso vem do catálogo da EJ: desconhecido e desativado são recusados")
    void shouldAcceptOnlyCataloguedCourses() {
        UUID tenantId = seeder.seedTenant("EJ Catálogo", "ej-catalogo");
        seeder.seedAccount(tenantId, "dono@catalogo.dev", "senha123", Standing.OWNER);
        String token = login("dono@catalogo.dev", "senha123");
        UUID processId = openProcess(token, "PS Catálogo", null);
        String path = publicProcess("ej-catalogo", processId) + "/applications";

        UUID aceito = courseOf("ej-catalogo");
        assertThat(post(path, application("dentro@example.com", aceito), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(post(path, application("fantasma@example.com", UUID.randomUUID()), null,
                ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        UUID descontinuado = seeder.seedCourse(tenantId, "Curso Descontinuado");
        patch("/v1/courses/" + descontinuado, Map.of("active", false), token, String.class);
        assertThat(post(path, application("tarde@example.com", descontinuado), null,
                ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("o catálogo público esconde curso desativado, mas a inscrição antiga mantém o rótulo")
    void shouldHideInactiveCourseFromPublicCatalogWithoutLosingHistory() {
        UUID tenantId = seeder.seedTenant("EJ Histórico Curso", "ej-hist-curso");
        seeder.seedAccount(tenantId, "dono@hist-curso.dev", "senha123", Standing.OWNER);
        String token = login("dono@hist-curso.dev", "senha123");
        UUID processId = openProcess(token, "PS Histórico", null);
        UUID saindo = seeder.seedCourse(tenantId, "Curso Que Sai");

        assertThat(post(publicProcess("ej-hist-curso", processId) + "/applications",
                application("veterano@example.com", saindo), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        patch("/v1/courses/" + saindo, Map.of("active", false), token, String.class);

        assertThat(publicCourses("ej-hist-curso")).doesNotContain("Curso Que Sai");
        assertThat(getWithToken(internalApplications(processId), token).getBody())
                .contains("Curso Que Sai");
    }

    @Test
    @DisplayName("notifica por e-mail o dono e os cargos com recruitment:read, mas não membros sem a permissão")
    void shouldNotifyMembersWithRecruitmentReadPermission() {
        UUID tenantId = seeder.seedTenant("EJ Notificação", "ej-notificacao");
        String ownerEmail = "dono@notificacao.dev";
        seeder.seedAccount(tenantId, ownerEmail, "senha123", Standing.OWNER);
        String token = login(ownerEmail, "senha123");

        UUID recruiterRoleId = seeder.seedCargo(tenantId, "Recrutamento");
        grantToRole(token, recruiterRoleId, "recruitment:read");
        seeder.seedAccount(tenantId, "recrutador@notificacao.dev", "senha123", Standing.MEMBER, recruiterRoleId);
        seeder.seedAccount(tenantId, "outro@notificacao.dev", "senha123", Standing.MEMBER);

        UUID processId = openProcess(token, "PS Notificação", null);
        submit("ej-notificacao", processId, "candidato@example.com");

        assertThat(sentRecipients(3)).containsExactlyInAnyOrder(
                "candidato@example.com", ownerEmail, "recrutador@notificacao.dev");
    }

    @Test
    @DisplayName("alumni não recebem a inscrição, mesmo herdando um cargo com recruitment:read")
    void shouldNotNotifyAlumni() {
        UUID tenantId = seeder.seedTenant("EJ Alumni", "ej-alumni");
        String ownerEmail = "dono@alumni.dev";
        seeder.seedAccount(tenantId, ownerEmail, "senha123", Standing.OWNER);
        String token = login(ownerEmail, "senha123");

        UUID recruiterRoleId = seeder.seedCargo(tenantId, "Recrutamento");
        grantToRole(token, recruiterRoleId, "recruitment:read");
        UUID aposentado = seeder.seedAccount(tenantId, "veterano@alumni.dev", "senha123",
                Standing.MEMBER, recruiterRoleId);
        assertThat(post("/v1/members/" + aposentado + "/retire", null, token, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID processId = openProcess(token, "PS Alumni", null);
        submit("ej-alumni", processId, "candidato@example.com");

        assertThat(sentRecipients(2)).containsExactlyInAnyOrder("candidato@example.com", ownerEmail);
    }

    @Test
    @DisplayName("permissão concedida direto ao membro notifica igual à concedida pelo cargo")
    void shouldNotifyOnMemberLevelGrant() {
        UUID tenantId = seeder.seedTenant("EJ Grant", "ej-grant");
        String ownerEmail = "dono@grant.dev";
        seeder.seedAccount(tenantId, ownerEmail, "senha123", Standing.OWNER);
        String token = login(ownerEmail, "senha123");

        UUID semCargo = seeder.seedAccount(tenantId, "avulso@grant.dev", "senha123", Standing.MEMBER);
        put("/v1/members/" + semCargo + "/permissions",
                Map.of("permissions", List.of("recruitment:read")), token, String.class);

        UUID processId = openProcess(token, "PS Grant", null);
        submit("ej-grant", processId, "candidato@example.com");

        assertThat(sentRecipients(3)).containsExactlyInAnyOrder(
                "candidato@example.com", ownerEmail, "avulso@grant.dev");
    }

    @Test
    @DisplayName("SMTP fora do ar não derruba a inscrição")
    void shouldPersistApplicationWhenSmtpFails() {
        String token = ownerOf("EJ SMTP", "ej-smtp", "dono@smtp.dev");
        UUID processId = openProcess(token, "PS SMTP", null);
        Mockito.doThrow(new MailSendException("smtp fora do ar"))
                .when(mailSender).send(Mockito.any(MimeMessage.class));

        assertThat(submit("ej-smtp", processId, "resiliente@example.com").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(getWithToken(internalApplications(processId), token).getBody())
                .contains("resiliente@example.com");
    }

    private List<String> sentRecipients(int expected) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(mailSender, Mockito.times(expected)).send(captor.capture());
        return captor.getAllValues().stream().map(CandidateApplicationIntegrationTest::onlyRecipient).toList();
    }

    private static String onlyRecipient(MimeMessage message) {
        try {
            Address[] recipients = message.getAllRecipients();
            assertThat(recipients).hasSize(1);
            return recipients[0].toString();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void grantToRole(String token, UUID roleId, String... permissions) {
        put("/v1/roles/" + roleId + "/permissions",
                Map.of("permissions", List.of(permissions)), token, String.class);
    }

    private ResponseEntity<CandidateApplicationReceiptResponse> submit(String slug, UUID processId, String email) {
        return post(publicProcess(slug, processId) + "/applications",
                application(email, courseOf(slug)), null, CandidateApplicationReceiptResponse.class);
    }

    /** O candidato anônimo descobre os cursos aceitos exatamente por aqui antes de preencher o form. */
    private UUID courseOf(String slug) {
        return get("/v1/public/" + slug + "/courses", null,
                new ParameterizedTypeReference<List<CourseCatalog.CourseOption>>() { })
                .getBody().getFirst().id();
    }

    private String publicCourses(String slug) {
        return get("/v1/public/" + slug + "/courses", null, String.class).getBody();
    }

    private String ownerOf(String organizationName, String slug, String email) {
        UUID tenantId = seeder.seedTenant(organizationName, slug);
        seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
        return login(email, "senha123");
    }

    private UUID createProcess(String token, String title) {
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
    }

    private UUID openProcess(String token, String title, String description) {
        UUID processId = post("/v1/recruitment/processes", new SelectionProcessRequest(title, description, null, null, null), token,
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

    private static SubmitCandidateApplicationRequest application(String email, UUID courseId) {
        return new SubmitCandidateApplicationRequest("João Silva", email, "31999998888",
                courseId, (short) 3, null, true);
    }
}
