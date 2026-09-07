package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern RESET_TOKEN = Pattern.compile("pwd_[A-Za-z0-9_-]+");

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
    @DisplayName("esqueci a senha: link do e-mail redefine, a senha antiga para de valer e o token queima")
    void shouldRecoverPasswordThroughEmailLink() {
        seedOwner("EJ Reset", "ej-reset", "dono@reset.dev", "senha-antiga-1");

        assertThat(forgot("dono@reset.dev").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String token = resetTokenFromEmail();

        assertThat(reset(token, "Senha@nova123").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login("dono@reset.dev", "Senha@nova123")).isNotBlank();
        assertThat(loginStatus("dono@reset.dev", "senha-antiga-1")).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Uso único: o mesmo link não redefine de novo.
        assertThat(reset(token, "Outra@senha123").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("forgot de e-mail sem conta responde o mesmo 202, e não manda e-mail nenhum")
    void shouldNotRevealWhoHasAnAccount() {
        assertThat(forgot("ninguem@reset.dev").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(MimeMessage.class));
    }

    @Test
    @DisplayName("pedir um link novo derruba o anterior: só o último redefine")
    void shouldKeepOnlyTheLatestLinkAlive() {
        seedOwner("EJ Reissue", "ej-reissue", "dono@reissue.dev", "senha-antiga-1");

        forgot("dono@reissue.dev");
        String primeiro = resetTokenFromEmail();
        Mockito.reset(mailSender);
        Mockito.when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        forgot("dono@reissue.dev");
        String segundo = resetTokenFromEmail();

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(reset(primeiro, "Senha@nova123").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reset(segundo, "Senha@nova123").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("trocar senha autenticado exige a senha atual; errá-la é 400 e não desloga")
    void shouldChangePasswordWhileAuthenticated() {
        seedOwner("EJ Change", "ej-change", "dono@change.dev", "senha-antiga-1");
        String sessao = login("dono@change.dev", "senha-antiga-1");

        ResponseEntity<String> errada = post("/v1/auth/password/change",
                Map.of("current_password", "chutando", "new_password", "Senha@nova123"), sessao, String.class);
        assertThat(errada.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errada.getBody()).contains("current_password");

        ResponseEntity<String> ok = post("/v1/auth/password/change",
                Map.of("current_password", "senha-antiga-1", "new_password", "Senha@nova123"), sessao, String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login("dono@change.dev", "Senha@nova123")).isNotBlank();
    }

    @Test
    @DisplayName("senha fora da política é 400 com a mensagem dizendo o que faltou")
    void shouldRejectPasswordOutsidePolicy() {
        seedOwner("EJ Política", "ej-politica", "dono@politica.dev", "senha-antiga-1");

        ResponseEntity<String> semMaiuscula = reset("pwd_qualquer", "senha@nova123");
        assertThat(semMaiuscula.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(semMaiuscula.getBody()).contains("letra maiúscula").doesNotContain("caractere especial");

        ResponseEntity<String> curta = reset("pwd_qualquer", "Ab@1");
        assertThat(curta.getBody()).contains("ao menos 8 caracteres");

        // A política barra antes de o token ser consultado: nem chega a dizer se o link vale.
        assertThat(semMaiuscula.getBody()).doesNotContain("Link de redefinição");
    }

    @Test
    @DisplayName("redefinir e trocar avisam por e-mail que a senha mudou")
    void shouldNotifyOnPasswordChange() {
        seedOwner("EJ Aviso", "ej-aviso", "dono@aviso.dev", "senha-antiga-1");

        forgot("dono@aviso.dev");
        String token = resetTokenFromEmail();
        Mockito.reset(mailSender);
        Mockito.when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));

        assertThat(reset(token, "Senha@nova123").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bodyOfSingleEmail()).contains("Sua senha foi alterada").contains("dono@aviso.dev");

        Mockito.reset(mailSender);
        Mockito.when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        String sessao = login("dono@aviso.dev", "Senha@nova123");
        assertThat(post("/v1/auth/password/change",
                Map.of("current_password", "Senha@nova123", "new_password", "Outra@senha123"),
                sessao, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bodyOfSingleEmail()).contains("Sua senha foi alterada");
    }

    @Test
    @DisplayName("change não é público: sem token é 401, mesmo com forgot e reset liberados")
    void shouldRequireAuthenticationToChange() {
        ResponseEntity<String> res = post("/v1/auth/password/change",
                Map.of("current_password", "x", "new_password", "Senha@nova123"), null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void seedOwner(String name, String slug, String email, String password) {
        UUID tenant = seeder.seedTenant(name, slug);
        seeder.seedAccount(tenant, email, password, Standing.OWNER);
    }

    private ResponseEntity<String> forgot(String email) {
        return post("/v1/auth/password/forgot", Map.of("email", email), null, String.class);
    }

    private ResponseEntity<String> reset(String token, String password) {
        return post("/v1/auth/password/reset", Map.of("token", token, "password", password), null, String.class);
    }

    private HttpStatus loginStatus(String email, String password) {
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        return HttpStatus.valueOf(res.getStatusCode().value());
    }

    private String bodyOfSingleEmail() {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(mailSender).send(sent.capture());
        try {
            return sent.getValue().getContent().toString();
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível ler o corpo do e-mail", e);
        }
    }

    /** O token cru só existe no e-mail: é de lá que o teste o lê, como o convidado leria. */
    private String resetTokenFromEmail() {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(mailSender).send(sent.capture());
        try {
            Matcher matcher = RESET_TOKEN.matcher(sent.getValue().getContent().toString());
            assertThat(matcher.find()).as("link pwd_ no corpo do e-mail").isTrue();
            return matcher.group();
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível ler o corpo do e-mail", e);
        }
    }
}
