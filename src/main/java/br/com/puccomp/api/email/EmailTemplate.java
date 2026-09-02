package br.com.puccomp.api.email;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderiza os HTML de {@code resources/email/} substituindo {@code {{chave}}}.
 *
 * <p>Todo valor é escapado. Nome de candidato e nome de EJ vêm de fora, e sem escapar um nome com
 * {@code <} quebraria o email inteiro — no melhor caso.
 */
final class EmailTemplate {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private EmailTemplate() { }

    static String render(String name, Map<String, String> model) {
        String rendered = load(name);
        for (Map.Entry<String, String> entry : model.entrySet())
            rendered = rendered.replace("{{" + entry.getKey() + "}}", escape(entry.getValue()));
        return rendered;
    }

    private static String load(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            try {
                return StreamUtils.copyToString(
                        new ClassPathResource("email/" + key + ".html").getInputStream(),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Template de email ausente: email/" + key + ".html", e);
            }
        });
    }

    static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
