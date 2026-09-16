package br.com.puccomp.api.email;

import br.com.puccomp.api.shared.text.Html;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compõe o e-mail a partir de {@code layout.html} e do miolo de cada aviso. A moldura — doctype,
 * resets, hacks de Outlook, paleta e rodapé — é a mesma em todo aviso e vive num lugar só.
 */
final class EmailTemplate {

    private static final String LAYOUT = "layout";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private EmailTemplate() { }

    static String render(String name, String subject, Map<String, String> model) {
        // Sem prévia própria vale o assunto: a moldura não pode vazar um marcador cru para a caixa
        // de entrada só porque um aviso não escreveu a linha de prévia.
        Map<String, String> chrome = new HashMap<>(model);
        chrome.put("subject", subject);
        chrome.putIfAbsent("preheader", subject);

        String page = replace(load(LAYOUT), chrome);
        return page.replace("{{content}}", replace(load(name), model));
    }

    /**
     * Uma passagem só, e não uma substituição por chave: em passagens sucessivas o valor já
     * inserido volta a ser varrido, e um nome digitado como marcador seria trocado na rodada
     * seguinte.
     *
     * <p>Todo valor é escapado, exceto chave terminada em {@code Html} — quem produz esse valor
     * escapa o que vem de fora com {@link Html#escape}.
     */
    private static String replace(String template, Map<String, String> model) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = model.containsKey(key)
                    ? (key.endsWith("Html") ? nullToEmpty(model.get(key)) : escape(model.get(key)))
                    : matcher.group();
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        return matcher.appendTail(rendered).toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
        return Html.escape(value);
    }
}
