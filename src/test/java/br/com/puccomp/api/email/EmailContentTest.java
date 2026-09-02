package br.com.puccomp.api.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmailContentTest {

    @Test
    @DisplayName("convite leva o nome da EJ, o link e a validade, sem placeholder sobrando")
    void shouldRenderInvitation() {
        EmailContent content = EmailContent.of(new EmailMessage.Invitation(
                "novato@ej.dev", "EJ Comp", "http://localhost/aceitar?token=abc", Duration.ofHours(72)));

        assertThat(content.subject()).isEqualTo("Convite para EJ Comp");
        assertThat(content.html())
                .contains("EJ Comp")
                .contains("http://localhost/aceitar?token=abc")
                .contains("72 horas")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("comprovante trata o candidato pelo primeiro nome e cita o processo")
    void shouldRenderCandidacyReceipt() {
        EmailContent content = EmailContent.of(new EmailMessage.CandidacyReceived(
                "ana@example.com", "Ana Lima Souza", "Processo Trainee 2026"));

        assertThat(content.subject()).isEqualTo("Inscrição confirmada — Processo Trainee 2026");
        assertThat(content.html()).contains("Olá, Ana!").contains("Processo Trainee 2026")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("nome vindo de fora é escapado: sem isso um '<' quebra o email inteiro")
    void shouldEscapeUserSuppliedValues() {
        EmailContent content = EmailContent.of(new EmailMessage.CandidacyReceived(
                "x@example.com", "<script>alert(1)</script>", "Processo <b>A</b>"));

        assertThat(content.html())
                .doesNotContain("<script>")
                .doesNotContain("Processo <b>A</b>")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("uma hora não vira '1 horas'")
    void shouldSingularizeSingleHour() {
        EmailContent content = EmailContent.of(new EmailMessage.Invitation(
                "a@b.dev", "EJ", "http://x", Duration.ofHours(1)));

        assertThat(content.html()).contains("1 hora").doesNotContain("1 horas");
    }
}
