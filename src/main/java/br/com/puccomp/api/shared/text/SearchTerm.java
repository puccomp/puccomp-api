package br.com.puccomp.api.shared.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Optional;

/**
 * Termo digitado virando padrão de LIKE, normalizado como as colunas geradas de busca — sem acento
 * e em minúsculas — para que "joao" encontre "João". Termo curto demais não filtra nada em vez de
 * varrer a tabela inteira, e % ou _ digitados valem como texto, não como curinga.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchTerm {

    private static final int MINIMUM_LENGTH = 2;

    public static Optional<String> like(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        if (normalized.length() < MINIMUM_LENGTH) return Optional.empty();
        return Optional.of(escapeWildcards(normalized));
    }

    private static String escapeWildcards(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
