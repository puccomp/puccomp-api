package br.com.puccomp.api.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    @Test
    @DisplayName("renderiza o template dentro do layout, com as variáveis recebidas")
    void shouldRenderTemplate() {
        String html = EmailTemplate.render("convite", "Convite para EJ Comp", Map.of(
                "organizationName", "EJ Comp",
                "acceptUrl", "http://localhost/aceitar?token=abc",
                "validFor", "72 horas"));

        assertThat(html)
                .contains("EJ Comp", "http://localhost/aceitar?token=abc", "72 horas")
                .contains("<!DOCTYPE html")
                .contains("<title>Convite para EJ Comp</title>")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("sem prévia declarada, a linha de prévia cai no assunto em vez de vazar o marcador")
    void shouldFallBackToSubjectAsPreheader() {
        String html = EmailTemplate.render("senha-alterada", "Sua senha mudou",
                Map.of("email", "membro@ejcomp.dev"));

        assertThat(html).doesNotContain("{{preheader}}").contains("Sua senha mudou");
    }

    /**
     * O renderizador ignora marcador sem valor, então um template que pede o que ninguém envia
     * entrega "{{organizationName}}" cru ao destinatário, em silêncio. A lista é o que cada disparo
     * envia; enviar a mais é inofensivo, e é o caso dos avisos que compartilham um disparo só.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "convite                | organizationName,acceptUrl,validFor",
            "redefinir-senha        | resetUrl,validFor",
            "senha-alterada         | email",
            "candidatura-confirmada | candidateName,processTitle,organizationName,submittedAt,protocol",
            "nova-inscricao         | candidateName,candidateEmail,course,processTitle,organizationName,submittedAt,cv",
            "processo-aberto        | processTitle,organizationName,openedAt",
            "inscricoes-encerradas  | candidateName,processTitle,organizationName,resultAt",
            "resultado-disponivel   | candidateName,processTitle,organizationName,resultAt",
            "processo-cancelado     | candidateName,processTitle,organizationName,resultAt",
            "resumo-inscricoes      | total,organizationName,since,processRowsHtml",
            "vinculo-alterado       | memberName,organizationName,situation,changedAt",
            "cargo-atribuido        | memberName,organizationName,role,department,changedAt",
            "convite-aceito         | inviteeName,inviteeEmail,organizationName,acceptedAt",
            "convite-expirando      | inviteeEmail,organizationName,expiresAt",
    })
    @DisplayName("o template não pede nenhuma variável além das que o disparo envia")
    void shouldOnlyAskForVariablesTheSenderProvides(String template, String provided) throws IOException {
        assertThat(placeholdersOf(read(template)))
                .as("template %s", template)
                .isSubsetOf(Set.of(provided.trim().split(",")));
    }

    @Test
    @DisplayName("o layout expõe exatamente os três encaixes que o renderizador preenche")
    void shouldDeclareLayoutSlots() throws IOException {
        assertThat(placeholdersOf(read("layout")))
                .containsExactlyInAnyOrder("subject", "preheader", "content");
    }

    @Test
    @DisplayName("nenhum template fixa o nome de uma EJ: o texto sai em nome de qualquer tenant")
    void shouldNotHardcodeAnyOrganization() throws IOException {
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath:email/*.html")) {
            String raw = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            assertThat(raw)
                    .as("template %s", resource.getFilename())
                    .doesNotContain("Empresa Júnior de Computação")
                    .doesNotContain("puccomp.com.br");
        }
    }

    @Test
    @DisplayName("escapa valores externos antes de inseri-los no HTML")
    void shouldEscapeVariables() {
        String html = EmailTemplate.render("candidatura-confirmada", "Inscrição confirmada", Map.of(
                "candidateName", "<script>alert(1)</script>",
                "processTitle", "Processo <b>A</b>"));

        assertThat(html)
                .doesNotContain("<script>", "Processo <b>A</b>")
                .contains("&lt;script&gt;");
    }

    /** O nome digitado por um candidato não pode virar marcador do layout. */
    @Test
    @DisplayName("valor externo não é reinterpretado como marcador do layout")
    void shouldNotReinterpretValuesAsPlaceholders() {
        String html = EmailTemplate.render("candidatura-confirmada", "Inscrição confirmada", Map.of(
                "candidateName", "{{preheader}}",
                "processTitle", "Processo A",
                "preheader", "prévia real"));

        assertThat(html).contains("{{preheader}}").contains("prévia real");
    }

    private static String read(String template) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("email/" + template + ".html").getInputStream(),
                StandardCharsets.UTF_8);
    }

    private static Set<String> placeholdersOf(String raw) {
        Matcher matcher = PLACEHOLDER.matcher(raw);
        return matcher.results().map(result -> result.group(1)).collect(Collectors.toSet());
    }
}
