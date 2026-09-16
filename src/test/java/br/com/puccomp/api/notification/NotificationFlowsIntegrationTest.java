package br.com.puccomp.api.notification;

import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os avisos que passaram a existir quando a notificação virou consumidora de eventos.
 *
 * <p>Tudo aqui atravessa um listener assíncrono, numa thread que não herda o tenant da requisição:
 * um erro nesse escopo aparece exatamente aqui.
 */
@Import(TestSeeder.class)
class NotificationFlowsIntegrationTest extends AbstractIntegrationTest {

    private static final long DELIVERY_TIMEOUT = 5_000;

    private static final long DRAIN_WINDOW = 750;

    private static final Pattern ACCEPT_TOKEN = Pattern.compile("token=([A-Za-z0-9_\\-]+)");

    @Autowired
    private TestSeeder seeder;

    @MockitoBean
    private JavaMailSender mailSender;

    /** Entrega na própria thread de quem envia; o salto assíncrono que resta é o listener. */
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
    @DisplayName("mudar a fase do processo avisa quem se inscreveu, uma vez por pessoa")
    void shouldNotifyCandidatesOnEveryPhaseChange() {
        String owner = ownerOf("EJ Fases", "ej-fases-not", "dono@fases.dev");
        UUID processId = openProcess(owner, "PS Fases");

        submit("ej-fases-not", processId, "ana@example.com");
        submit("ej-fases-not", processId, "bruno@example.com");
        drainMail();

        changeStatus(owner, processId, SelectionProcessStatus.IN_REVIEW);
        assertThat(delivered(2)).containsExactlyInAnyOrder(
                entry("ana@example.com", "Inscrições encerradas: PS Fases"),
                entry("bruno@example.com", "Inscrições encerradas: PS Fases"));

        drainMail();
        changeStatus(owner, processId, SelectionProcessStatus.CLOSED);
        assertThat(delivered(2)).containsExactlyInAnyOrder(
                entry("ana@example.com", "Resultado disponível: PS Fases"),
                entry("bruno@example.com", "Resultado disponível: PS Fases"));
    }

    @Test
    @DisplayName("aposentar e reativar avisam o próprio membro, que perdeu ou recuperou acesso")
    void shouldNotifyMemberOnStatusChange() {
        UUID tenant = seeder.seedTenant("EJ Vínculo", "ej-vinculo-not");
        seeder.seedAccount(tenant, "dono@vinculo.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro@vinculo.dev", "senha123", Standing.MEMBER);
        String owner = login("dono@vinculo.dev", "senha123");

        assertThat(post("/v1/members/" + memberId + "/retire", null, owner, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delivered(1))
                .containsExactly(entry("membro@vinculo.dev", "Você agora é alumnus"));

        drainMail();
        assertThat(post("/v1/members/" + memberId + "/reactivate", null, owner, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delivered(1))
                .containsExactly(entry("membro@vinculo.dev", "Seu vínculo foi reativado"));
    }

    @Test
    @DisplayName("mudar o cargo avisa o membro: o acesso dele acompanha a atribuição")
    void shouldNotifyMemberOnAssignment() {
        UUID tenant = seeder.seedTenant("EJ Cargo", "ej-cargo-not");
        seeder.seedAccount(tenant, "dono@cargo.dev", "senha123", Standing.OWNER);
        UUID memberId = seeder.seedAccount(tenant, "membro@cargo.dev", "senha123", Standing.MEMBER);
        String owner = login("dono@cargo.dev", "senha123");
        UUID roleId = seeder.seedCargo(tenant, "Projetos");

        assertThat(put("/v1/members/" + memberId + "/assignment",
                Map.of("role_id", roleId.toString()), owner, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(delivered(1))
                .containsExactly(entry("membro@cargo.dev", "Sua atribuição mudou"));
    }

    @Test
    @DisplayName("aceitar o convite avisa quem convidou, que não teria como saber sozinho")
    void shouldNotifyInviterOnAcceptance() {
        UUID tenant = seeder.seedTenant("EJ Convite", "ej-convite-not");
        seeder.seedAccount(tenant, "dono@convite-not.dev", "senha123", Standing.OWNER);
        UUID courseId = seeder.seedCourse(tenant, "Computação");
        String owner = login("dono@convite-not.dev", "senha123");

        assertThat(post("/v1/invitations", Map.of("email", "novato@convite-not.dev"), owner, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = acceptToken();

        drainMail();
        assertThat(post("/v1/invitations/accept", Map.of(
                "token", token, "name", "Novato Silva", "password", "SenhaForte@123",
                "course_id", courseId.toString()), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(delivered(1))
                .containsExactly(entry("dono@convite-not.dev", "Convite aceito: Novato Silva"));
    }

    private record Delivered(String recipient, String subject) { }

    private static Delivered entry(String recipient, String subject) {
        return new Delivered(recipient, subject);
    }

    private List<Delivered> delivered(int expected) {
        return captured(expected).stream()
                .map(message -> new Delivered(onlyRecipient(message), subjectOf(message)))
                .toList();
    }

    private List<MimeMessage> captured(int expected) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(mailSender, Mockito.timeout(DELIVERY_TIMEOUT).times(expected))
                .send(captor.capture());
        return captor.getAllValues();
    }

    /** O token é guardado como hash: o link só existe no e-mail. */
    private String acceptToken() {
        try {
            String body = String.valueOf(captured(1).getFirst().getContent());
            Matcher matcher = ACCEPT_TOKEN.matcher(body);
            assertThat(matcher.find()).as("link de aceite no corpo do convite").isTrue();
            return matcher.group(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Zera os avisos da montagem do cenário, cujo número varia — o que se mede vem depois. */
    private void drainMail() {
        Mockito.verify(mailSender, Mockito.after(DRAIN_WINDOW).atLeast(0))
                .send(Mockito.any(MimeMessage.class));
        stubMailTransport();
    }

    private String ownerOf(String name, String slug, String email) {
        UUID tenant = seeder.seedTenant(name, slug);
        seeder.seedAccount(tenant, email, "senha123", Standing.OWNER);
        seeder.seedCourse(tenant, "Computação");
        return login(email, "senha123");
    }

    private UUID openProcess(String token, String title) {
        UUID processId = post("/v1/recruitment/processes",
                new SelectionProcessRequest(title, null, null, null, null, null, null), token,
                SelectionProcessResponse.class).getBody().id();
        changeStatus(token, processId, SelectionProcessStatus.OPEN);
        drainMail();
        return processId;
    }

    private void changeStatus(String token, UUID processId, SelectionProcessStatus status) {
        assertThat(patch("/v1/recruitment/processes/" + processId + "/status",
                new ChangeStatusRequest(status), token, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void submit(String slug, UUID processId, String email) {
        UUID courseId = courseOf(slug);
        assertThat(post("/v1/public/" + slug + "/processes/" + processId + "/applications",
                Map.of("full_name", "Candidato " + email, "email", email, "phone", "31999990000",
                        "course_id", courseId.toString(), "privacy_consent", true),
                null, String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    private UUID courseOf(String slug) {
        var courses = get("/v1/public/" + slug + "/courses", null, List.class).getBody();
        return UUID.fromString((String) ((Map<String, Object>) courses.getFirst()).get("id"));
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

    private static String subjectOf(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }
}
