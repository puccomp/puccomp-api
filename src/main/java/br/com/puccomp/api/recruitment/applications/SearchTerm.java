package br.com.puccomp.api.recruitment.applications;

import java.text.Normalizer;
import java.util.Optional;

/**
 * Normaliza o termo digitado do mesmo jeito que a coluna {@code search_name} é gerada no banco —
 * sem acento e em minúsculas — para que "joao" encontre "João", que é como o recrutador digita.
 */
final class SearchTerm {

    private static final int MINIMUM_LENGTH = 2;

    private SearchTerm() { }

    static Optional<String> like(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        if (normalized.length() < MINIMUM_LENGTH) return Optional.empty();
        return Optional.of("%" + escapeWildcards(normalized) + "%");
    }

    /** Sem isso, um termo com % ou _ viraria curinga e casaria com o que o usuário não pediu. */
    private static String escapeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
