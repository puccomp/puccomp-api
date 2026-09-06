package br.com.puccomp.api.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateTest {

    @Test
    @DisplayName("renderiza o template com as variáveis recebidas")
    void shouldRenderTemplate() {
        String html = EmailTemplate.render("convite", Map.of(
                "organizationName", "EJ Comp",
                "acceptUrl", "http://localhost/aceitar?token=abc",
                "validFor", "72 horas"));

        assertThat(html)
                .contains("EJ Comp", "http://localhost/aceitar?token=abc", "72 horas")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("escapa valores externos antes de inseri-los no HTML")
    void shouldEscapeVariables() {
        String html = EmailTemplate.render("candidatura-confirmada", Map.of(
                "candidateName", "<script>alert(1)</script>",
                "processTitle", "Processo <b>A</b>"));

        assertThat(html)
                .doesNotContain("<script>", "Processo <b>A</b>")
                .contains("&lt;script&gt;");
    }
}
