package br.com.puccomp.api.shared.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Escape de texto que vai para dentro de HTML — corpo de e-mail, hoje. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Html {

    public static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
