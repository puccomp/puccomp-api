package br.com.puccomp.api.shared.text;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Slugs {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public static String slugify(String value) {
        if (value == null) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    public static boolean isValid(String slug) {
        return slug != null && VALID.matcher(slug).matches();
    }

    public static String uniqueSlug(String base, Predicate<String> exists) {
        if (!exists.test(base)) return base;
        int suffix = 1;
        String candidate = base + "-" + suffix;
        while (exists.test(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }
}
