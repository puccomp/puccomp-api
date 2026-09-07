package br.com.puccomp.api.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /**
     * O renderizador troca {{x}} por valor e ignora o que sobra, então template com variável a mais
     * entrega "{{organizationName}}" cru para o destinatário, em silêncio. Este teste é o que impede
     * isso: a lista é o contrato entre cada template e quem o dispara.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "convite                | organizationName,acceptUrl,validFor",
            "redefinir-senha        | resetUrl,validFor",
            "senha-alterada         | email",
            "candidatura-confirmada | candidateName,processTitle,organizationName,submittedAt,protocol",
            "nova-inscricao         | candidateName,candidateEmail,course,processTitle,organizationName,submittedAt,cv",
    })
    @DisplayName("as variáveis do template batem exatamente com as que o código envia")
    void shouldDeclareExactlyTheVariablesTheSenderProvides(String template, String expected) throws IOException {
        String raw = StreamUtils.copyToString(
                new ClassPathResource("email/" + template + ".html").getInputStream(), StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("\\{\\{(\\w+)}}").matcher(raw);
        Set<String> found = matcher.results().map(r -> r.group(1)).collect(Collectors.toSet());

        assertThat(found).containsExactlyInAnyOrderElementsOf(Set.of(expected.trim().split(",")));
    }

    @Test
    @DisplayName("nenhum template fixa o nome de uma EJ: o texto sai em nome de qualquer tenant")
    void shouldNotHardcodeAnyOrganization() throws IOException {
        for (String template : new String[] {"convite", "redefinir-senha", "senha-alterada",
                "candidatura-confirmada", "nova-inscricao"}) {
            String raw = StreamUtils.copyToString(
                    new ClassPathResource("email/" + template + ".html").getInputStream(), StandardCharsets.UTF_8);
            assertThat(raw)
                    .as("template %s", template)
                    .doesNotContain("Empresa Júnior de Computação")
                    .doesNotContain("puccomp.com.br");
        }
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
