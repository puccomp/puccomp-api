package br.com.puccomp.api.recruitment.processes;

import java.text.Normalizer;
import java.util.Optional;

/** Normaliza a busca do mesmo modo que a coluna gerada {@code search_title}. */
final class ProcessSearchTerm {

    private static final int MINIMUM_LENGTH = 2;

    private ProcessSearchTerm() { }

    static Optional<String> like(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        if (normalized.length() < MINIMUM_LENGTH) return Optional.empty();
        return Optional.of("%" + escapeWildcards(normalized) + "%");
    }

    private static String escapeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
