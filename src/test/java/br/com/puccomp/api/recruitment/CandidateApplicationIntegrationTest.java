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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Import(TestSeeder.class)
class CandidateApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestSeeder seeder;

    private final ObjectMapper mapper = new ObjectMapper();

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

    @Test
    @DisplayName("faixa de período do processo recusa quem está fora dela e quem não informou")
    void shouldEnforceProcessTermRange() {
        String token = ownerOf("EJ Elegibilidade", "ej-elegibilidade", "dono@elegibilidade.dev");
        UUID processId = post("/v1/recruitment/processes",
                new SelectionProcessRequest("PS Elegibilidade", null, null, null, null,
                        (short) 3, (short) 6),
                token, SelectionProcessResponse.class).getBody().id();
        patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token, SelectionProcessResponse.class);

        String path = publicProcess("ej-elegibilidade", processId) + "/applications";
        UUID courseId = courseOf("ej-elegibilidade");

        assertThat(post(path, candidate("dentro@example.com", courseId, (short) 4), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> cedo = post(path,
                candidate("cedo@example.com", courseId, (short) 2), null, ErrorResponse.class);
        assertThat(cedo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cedo.getBody().message()).contains("do 3º ao 6º período");

        assertThat(post(path, candidate("tarde@example.com", courseId, (short) 7), null,
                ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, candidate("sem-periodo@example.com", courseId, null), null,
                ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("processo sem faixa segue aceitando qualquer período, inclusive nenhum")
    void shouldAcceptAnyTermWhenProcessDeclaresNoRange() {
        String token = ownerOf("EJ Sem Faixa", "ej-sem-faixa", "dono@sem-faixa.dev");
        UUID processId = openProcess(token, "PS Sem Faixa", null);
        String path = publicProcess("ej-sem-faixa", processId) + "/applications";
        UUID courseId = courseOf("ej-sem-faixa");

        assertThat(post(path, candidate("primeiro@example.com", courseId, (short) 1), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(path, candidate("indefinido@example.com", courseId, null), null,
                CandidateApplicationReceiptResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("recusa faixa de período invertida na criação do processo")
    void shouldRejectInvertedTermRange() {
        String token = ownerOf("EJ Faixa Invertida", "ej-faixa-invertida", "dono@faixa-invertida.dev");

        assertThat(post("/v1/recruitment/processes",
                new SelectionProcessRequest("Invertida", null, null, null, null, (short) 8, (short) 2),
                token, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("busca por nome ignora acento e caixa, que é como o recrutador digita")
    void shouldSearchByNameIgnoringAccents() {
        String token = ownerOf("EJ Busca", "ej-busca", "dono@busca.dev");
        UUID processId = openProcess(token, "PS Busca", null);
        UUID courseId = courseOf("ej-busca");
        submitNamed("ej-busca", processId, "João Conceição", "joao@example.com", courseId);
        submitNamed("ej-busca", processId, "Maria Andrade", "maria@example.com", courseId);

        // Sem acento no termo: quem normaliza é a coluna gerada.
        assertThat(names(search(token, processId, "joao"))).containsExactly("João Conceição");
        assertThat(names(search(token, processId, "CONCEICAO"))).containsExactly("João Conceição");
        // Com acento no termo: aqui quem tem que normalizar é o Java, senão não casa com a coluna.
        assertThat(names(search(token, processId, "Conceição"))).containsExactly("João Conceição");
        assertThat(names(search(token, processId, "JOÃO"))).containsExactly("João Conceição");
        assertThat(names(search(token, processId, "andrade"))).containsExactly("Maria Andrade");
        assertThat(names(search(token, processId, "zzz"))).isEmpty();
    }

    @Test
    @DisplayName("busca também casa e-mail, e termo curto demais é ignorado em vez de filtrar")
    void shouldSearchByEmailAndIgnoreShortTerms() {
        String token = ownerOf("EJ Busca Email", "ej-busca-email", "dono@busca-email.dev");
        UUID processId = openProcess(token, "PS Email", null);
        UUID courseId = courseOf("ej-busca-email");
        submitNamed("ej-busca-email", processId, "Ana Lima", "ana.lima@empresa.com", courseId);
        submitNamed("ej-busca-email", processId, "Bruno Reis", "bruno@outra.com", courseId);

        assertThat(names(search(token, processId, "empresa.com"))).containsExactly("Ana Lima");
        assertThat(names(search(token, processId, "a"))).hasSize(2);
        assertThat(names(search(token, processId, "   "))).hasSize(2);
    }

    @Test
    @DisplayName("curinga digitado é tratado como texto, não como coringa de LIKE")
    void shouldNotLetUserWildcardsLeak() {
        String token = ownerOf("EJ Curinga", "ej-curinga", "dono@curinga.dev");
        UUID processId = openProcess(token, "PS Curinga", null);
        UUID courseId = courseOf("ej-curinga");
        submitNamed("ej-curinga", processId, "Ana Lima", "ana@example.com", courseId);
        submitNamed("ej-curinga", processId, "Bruno Reis", "bruno@example.com", courseId);

        assertThat(names(search(token, processId, "%%"))).isEmpty();
        assertThat(names(search(token, processId, "a_a"))).isEmpty();
    }

    @Test
    @DisplayName("a busca da EJ inteira acha a pessoa em qualquer processo e diz de qual")
    void shouldSearchAcrossProcesses() {
        String token = ownerOf("EJ Recorrentes", "ej-recorrentes", "dono@recorrentes.dev");
        UUID courseId = courseOf("ej-recorrentes");
        UUID anterior = openProcess(token, "PS 2025.2", null);
        UUID atual = openProcess(token, "PS 2026.1", null);
        submitNamed("ej-recorrentes", anterior, "Carla Souza", "carla@example.com", courseId);
        submitNamed("ej-recorrentes", atual, "Carla Souza", "carla@example.com", courseId);
        submitNamed("ej-recorrentes", atual, "Outro Alguém", "outro@example.com", courseId);

        JsonNode encontrados = searchAll(token, "carla");
        assertThat(names(encontrados)).containsExactlyInAnyOrder("Carla Souza", "Carla Souza");
        assertThat(encontrados.path("content").findValuesAsText("name"))
                .contains("PS 2025.2", "PS 2026.1");

        assertThat(names(searchAll(token, null))).hasSize(3);
    }

    @Test
    @DisplayName("a busca da EJ inteira não enxerga candidato de outra EJ")
    void shouldNotSearchAcrossTenants() {
        String tokenA = ownerOf("EJ Busca Alpha", "ej-busca-alpha", "dono@busca-alpha.dev");
        String tokenB = ownerOf("EJ Busca Beta", "ej-busca-beta", "dono@busca-beta.dev");
        UUID processoA = openProcess(tokenA, "PS Alpha", null);
        submitNamed("ej-busca-alpha", processoA, "Exclusiva Alpha", "exclusiva@example.com", courseOf("ej-busca-alpha"));

        assertThat(names(searchAll(tokenA, "exclusiva"))).containsExactly("Exclusiva Alpha");
        assertThat(names(searchAll(tokenB, "exclusiva"))).isEmpty();
    }

    @Test
    @DisplayName("filtra a listagem por curso, período, currículo, links e janela de envio")
    void shouldFilterApplications() {
        UUID tenantId = seeder.seedTenant("EJ Filtros", "ej-filtros");
        seeder.seedAccount(tenantId, "dono@filtros.dev", "senha123", Standing.OWNER);
        String token = login("dono@filtros.dev", "senha123");
        UUID processId = openProcess(token, "PS Filtros", null);
        String path = publicProcess("ej-filtros", processId) + "/applications";

        UUID computacao = courseOf("ej-filtros");
        UUID design = seeder.seedCourse(tenantId, "Design");

        post(path, candidate("terceiro@example.com", computacao, (short) 3), null, String.class);
        post(path, candidate("oitavo@example.com", computacao, (short) 8), null, String.class);
        post(path, candidate("designer@example.com", design, (short) 3), null, String.class);

        assertThat(emails(filtered(token, processId, "?course_id={v}", computacao)))
                .containsExactlyInAnyOrder("terceiro@example.com", "oitavo@example.com");
        assertThat(emails(filtered(token, processId, "?course_id={v}", design)))
                .containsExactly("designer@example.com");
        assertThat(emails(filtered(token, processId, "?min_term={v}", "5")))
                .containsExactly("oitavo@example.com");
        assertThat(emails(filtered(token, processId, "?max_term={v}", "3")))
                .containsExactlyInAnyOrder("terceiro@example.com", "designer@example.com");
        assertThat(emails(filtered(token, processId, "?has_cv={v}", "true"))).isEmpty();
        assertThat(emails(filtered(token, processId, "?has_cv={v}", "false"))).hasSize(3);

        // Link e currículo são anexos independentes: quem mandou um não entra no filtro do outro.
        post(path, new SubmitCandidateApplicationRequest("Com Link", "portfolio@example.com",
                "31999998888", design, (short) 3, List.of("https://github.com/exemplo"), true),
                null, String.class);
        assertThat(emails(filtered(token, processId, "?has_links={v}", "true")))
                .containsExactly("portfolio@example.com");
        assertThat(emails(filtered(token, processId, "?has_links={v}", "false")))
                .containsExactlyInAnyOrder("terceiro@example.com", "oitavo@example.com",
                        "designer@example.com");
        assertThat(emails(filtered(token, processId, "?has_cv={v}", "true"))).isEmpty();
    }

    /**
     * A pergunta que o endpoint de busca da EJ existe para responder — "essa pessoa já se inscreveu
     * antes?" — agora está na própria linha, sem o cliente varrer as páginas atrás de e-mail repetido.
     */
    @Test
    @DisplayName("cada linha traz quantas vezes o e-mail já se inscreveu na EJ, e desde quando")
    void shouldReportCandidateRecurrence() {
        String token = ownerOf("EJ Reincidencia", "ej-reincidencia", "dono@reincidencia.dev");
        UUID courseId = courseOf("ej-reincidencia");
        UUID anterior = openProcess(token, "PS 2025.2", null);
        UUID atual = openProcess(token, "PS 2026.1", null);

        submitNamed("ej-reincidencia", anterior, "Carla Souza", "carla@example.com", courseId);
        // Caixa diferente é a mesma pessoa: o agrupamento normaliza como a unicidade por processo.
        submitNamed("ej-reincidencia", atual, "Carla Souza", "CARLA@example.com", courseId);
        submitNamed("ej-reincidencia", atual, "Estreante Silva", "estreante@example.com", courseId);

        JsonNode pagina = getJson(internalApplications(atual), token);
        JsonNode reincidente = row(pagina, "carla@example.com");
        JsonNode estreante = row(pagina, "estreante@example.com");

        assertThat(reincidente.path("applications_count").asInt()).isEqualTo(2);
        assertThat(Instant.parse(reincidente.path("first_applied_at").asText()))
                .isBefore(Instant.parse(reincidente.path("submitted_at").asText()));

        assertThat(estreante.path("applications_count").asInt()).isEqualTo(1);
        assertThat(estreante.path("first_applied_at").asText())
                .isEqualTo(estreante.path("submitted_at").asText());

        // O histórico descreve a EJ inteira: filtrar a página não o encolhe.
        assertThat(row(getJson(internalApplications(atual) + "?q={q}", token, "carla"),
                "carla@example.com").path("applications_count").asInt()).isEqualTo(2);
        assertThat(row(searchAll(token, "carla"), "carla@example.com")
                .path("applications_count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("o mesmo e-mail em outra EJ não engorda o histórico de quem consulta")
    void shouldCountRecurrenceWithinTheTenantOnly() {
        String tokenA = ownerOf("EJ Historico Alpha", "ej-historico-alpha", "dono@historico-alpha.dev");
        String tokenB = ownerOf("EJ Historico Beta", "ej-historico-beta", "dono@historico-beta.dev");
        UUID processoA = openProcess(tokenA, "PS Alpha", null);
        UUID processoB = openProcess(tokenB, "PS Beta", null);

        submitNamed("ej-historico-alpha", processoA, "Multi EJ", "multi@example.com",
                courseOf("ej-historico-alpha"));
        submitNamed("ej-historico-beta", processoB, "Multi EJ", "multi@example.com",
                courseOf("ej-historico-beta"));

        assertThat(row(getJson(internalApplications(processoA), tokenA), "multi@example.com")
                .path("applications_count").asInt()).isEqualTo(1);
        assertThat(row(getJson(internalApplications(processoB), tokenB), "multi@example.com")
                .path("applications_count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("os filtros combinam entre si e valem também na busca da EJ inteira")
    void shouldCombineFiltersAndApplyThemToTenantWideSearch() {
        UUID tenantId = seeder.seedTenant("EJ Combina", "ej-combina");
        seeder.seedAccount(tenantId, "dono@combina.dev", "senha123", Standing.OWNER);
        String token = login("dono@combina.dev", "senha123");
        UUID processId = openProcess(token, "PS Combina", null);
        String path = publicProcess("ej-combina", processId) + "/applications";

        UUID computacao = courseOf("ej-combina");
        UUID design = seeder.seedCourse(tenantId, "Design");
        submitNamedWith(path, "Ana Alvo", "alvo@example.com", computacao, (short) 4);
        submitNamedWith(path, "Bruno Curso", "curso@example.com", design, (short) 4);
        submitNamedWith(path, "Carla Periodo", "periodo@example.com", computacao, (short) 9);

        JsonNode combinado = getJson(internalApplications(processId) + "?course_id={c}&max_term={t}",
                token, computacao, "5");
        assertThat(emails(combinado)).containsExactly("alvo@example.com");

        JsonNode naEjInteira = getJson("/v1/recruitment/applications?course_id={c}&max_term={t}",
                token, computacao, "5");
        assertThat(emails(naEjInteira)).containsExactly("alvo@example.com");
    }

    @Test
    @DisplayName("o resumo agrega curso, período, currículo e a curva de chegada")
    void shouldSummarizeApplications() {
        UUID tenantId = seeder.seedTenant("EJ Resumo", "ej-resumo");
        seeder.seedAccount(tenantId, "dono@resumo.dev", "senha123", Standing.OWNER);
        String token = login("dono@resumo.dev", "senha123");
        UUID processId = openProcess(token, "PS Resumo", null);
        String path = publicProcess("ej-resumo", processId) + "/applications";

        UUID computacao = courseOf("ej-resumo");
        UUID design = seeder.seedCourse(tenantId, "Design");
        post(path, candidate("a@example.com", computacao, (short) 2), null, String.class);
        post(path, candidate("b@example.com", computacao, (short) 4), null, String.class);
        post(path, candidate("c@example.com", computacao, (short) 6), null, String.class);
        post(path, candidate("d@example.com", design, (short) 8), null, String.class);
        post(path, new SubmitCandidateApplicationRequest("Sem Periodo", "e@example.com", "31999998888",
                design, null, List.of("https://github.com/e"), true), null, String.class);

        JsonNode resumo = getJson(internalApplications(processId) + "/summary", token);

        assertThat(resumo.path("total").path("value").asInt()).isEqualTo(5);
        assertThat(resumo.path("total").path("previous").isNull()).isTrue();
        assertThat(resumo.path("with_cv").path("value").asInt()).isZero();
        assertThat(resumo.path("with_links").path("value").asInt()).isEqualTo(1);
        assertThat(resumo.path("distinct_courses").asInt()).isEqualTo(2);

        // Da maior contagem para a menor: Computação com 3, Design com 2.
        assertThat(resumo.path("by_course").get(0).path("key").path("id").asText())
                .isEqualTo(computacao.toString());
        assertThat(resumo.path("by_course").get(0).path("key").path("name").asText())
                .isEqualTo("Ciência da Computação");
        assertThat(resumo.path("by_course").get(0).path("count").asInt()).isEqualTo(3);
        assertThat(resumo.path("by_course").get(0).path("share").asDouble()).isEqualTo(3d / 5);
        assertThat(resumo.path("by_course").get(1).path("count").asInt()).isEqualTo(2);

        // Períodos 2, 4, 6, 8 e um sem vínculo, que vai por último com id nulo.
        assertThat(resumo.path("by_term")).hasSize(5);
        assertThat(resumo.path("by_term").get(0).path("key").path("id").asText()).isEqualTo("2");
        assertThat(resumo.path("by_term").get(4).path("key").path("id").isNull()).isTrue();
        assertThat(resumo.path("by_term").get(4).path("key").path("name").asText())
                .isEqualTo("Não informado");

        // Quatro informaram período: a mediana cai no segundo valor.
        assertThat(resumo.path("median_term").asInt()).isEqualTo(4);

        // Tudo enviado agora, então um dia só, que é o pico, com 100% do volume.
        assertThat(resumo.path("by_day")).hasSize(1);
        assertThat(resumo.path("by_day").get(0).path("value").asInt()).isEqualTo(5);
        assertThat(resumo.path("peak_day").path("count").asInt()).isEqualTo(5);
        assertThat(resumo.path("last_day_share").asDouble()).isEqualTo(1.0);
        assertThat(resumo.path("first_submitted_at").isNull()).isFalse();

        // As distribuições somam o total e os shares fecham em 1.
        assertThat(sumOfCounts(resumo.path("by_course"))).isEqualTo(5);
        assertThat(sumOfCounts(resumo.path("by_term"))).isEqualTo(5);
        assertThat(sumOfShares(resumo.path("by_course"))).isCloseTo(1d, within(1e-9));
    }

    @Test
    @DisplayName("resumo de processo sem inscrição volta zerado, não 404 nem nulo")
    void shouldSummarizeEmptyProcess() {
        String token = ownerOf("EJ Resumo Vazio", "ej-resumo-vazio", "dono@resumo-vazio.dev");
        UUID processId = openProcess(token, "PS Vazio", null);

        JsonNode resumo = getJson(internalApplications(processId) + "/summary", token);
        assertThat(resumo.path("total").path("value").asInt()).isZero();
        assertThat(resumo.path("by_course")).isEmpty();
        assertThat(resumo.path("by_day")).isEmpty();
        assertThat(resumo.path("peak_day").isNull()).isTrue();
        assertThat(resumo.path("last_day_share").isNull()).isTrue();
        assertThat(resumo.path("median_term").isNull()).isTrue();
    }

    @Test
    @DisplayName("o resumo não enxerga inscrição de outra EJ")
    void shouldNotSummarizeAcrossTenants() {
        String tokenA = ownerOf("EJ Resumo Alpha", "ej-resumo-alpha", "dono@resumo-alpha.dev");
        String tokenB = ownerOf("EJ Resumo Beta", "ej-resumo-beta", "dono@resumo-beta.dev");
        UUID processoA = openProcess(tokenA, "PS Alpha", null);
        submit("ej-resumo-alpha", processoA, "candidato@example.com");

        assertThat(getJson(internalApplications(processoA) + "/summary", tokenA)
                .path("total").path("value").asInt()).isEqualTo(1);
        assertThat(getWithToken(internalApplications(processoA) + "/summary", tokenB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static long sumOfCounts(JsonNode distribution) {
        long sum = 0;
        for (JsonNode slice : distribution) sum += slice.path("count").asLong();
        return sum;
    }

    private static double sumOfShares(JsonNode distribution) {
        double sum = 0;
        for (JsonNode slice : distribution) sum += slice.path("share").asDouble();
        return sum;
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
        return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
    }

    private UUID openProcess(String token, String title, String description) {
        UUID processId = post("/v1/recruitment/processes", new SelectionProcessRequest(title, description, null, null, null, null, null), token,
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

    private static SubmitCandidateApplicationRequest candidate(String email, UUID courseId, Short term) {
        return new SubmitCandidateApplicationRequest("João Silva", email, "31999998888",
                courseId, term, null, true);
    }

    private static SubmitCandidateApplicationRequest application(String email, UUID courseId) {
        return new SubmitCandidateApplicationRequest("João Silva", email, "31999998888",
                courseId, (short) 3, null, true);
    }

    private void submitNamed(String slug, UUID processId, String fullName, String email, UUID courseId) {
        var response = post(publicProcess(slug, processId) + "/applications",
                new SubmitCandidateApplicationRequest(fullName, email, "31999998888",
                        courseId, (short) 3, null, true),
                null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode search(String token, UUID processId, String q) {
        return getJson(internalApplications(processId) + "?q={q}", token, q);
    }

    private JsonNode searchAll(String token, String q) {
        return q == null
                ? getJson("/v1/recruitment/applications", token)
                : getJson("/v1/recruitment/applications?q={q}", token, q);
    }

    /**
     * O termo vai como variável de URI, não concatenado: assim o Spring o codifica uma vez só.
     * Pré-codificar aqui faria o RestTemplate codificar de novo, e o servidor receberia o %XX cru.
     */
    private JsonNode getJson(String path, String token, Object... uriVariables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return readJson(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class,
                uriVariables).getBody());
    }

    private static java.util.List<String> names(JsonNode page) {
        return page.path("content").findValuesAsText("full_name");
    }

    private JsonNode readJson(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode filtered(String token, UUID processId, String query, Object value) {
        return getJson(internalApplications(processId) + query, token, value);
    }

    private void submitNamedWith(String path, String fullName, String email, UUID courseId, Short term) {
        post(path, new SubmitCandidateApplicationRequest(fullName, email, "31999998888",
                courseId, term, null, true), null, String.class);
    }

    private static java.util.List<String> emails(JsonNode page) {
        return page.path("content").findValuesAsText("email");
    }

    private static JsonNode row(JsonNode page, String email) {
        for (JsonNode entry : page.path("content"))
            if (entry.path("email").asText().equalsIgnoreCase(email)) return entry;
        throw new AssertionError("a inscrição de " + email + " não veio na página");
    }
}
