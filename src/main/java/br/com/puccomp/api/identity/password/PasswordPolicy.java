package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Definição única do que é uma senha aceitável. Existe para que o aceite de convite, a redefinição
 * e a troca não divirjam: senha recusada num caminho e aceita no outro deixa a pessoa presa —
 * conseguiria criar no convite uma senha que a redefinição depois se recusaria a restaurar.
 *
 * <p>A regra mora aqui em vez de nas anotações porque nem todo ponto alcança bean validation: no
 * aceite de convite o mesmo campo ora define uma senha, ora prova a posse de conta que já existe.
 * {@link ValidPassword} é a porta declarativa para os DTOs; {@link #validateNewPassword} é a
 * imperativa para o resto. As duas chamam {@link #violations}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /** BCrypt trunca em 72 bytes: sem teto, o resto da senha seria ignorado em silêncio. */
    public static final int MAX_LENGTH = 72;

    /** {@code \p{Lu}} e não {@code A-Z}: "Ática" começa com maiúscula, e o público aqui é brasileiro. */
    private static final Pattern UPPERCASE = Pattern.compile("\\p{Lu}");

    /**
     * Especial é o que não é letra nem dígito — em qualquer alfabeto. Definir pela negativa evita a
     * lista de símbolos permitidos que sempre esquece algum, e impede que "ç" ou "é" contem como
     * especial só por não serem ASCII.
     */
    private static final Pattern SPECIAL = Pattern.compile("[^\\p{L}\\p{N}]");

    private PasswordPolicy() { }

    /** O que falta na senha, em linguagem de usuário. Lista vazia significa que ela passa. */
    public static List<String> violations(String password) {
        List<String> missing = new ArrayList<>(3);
        if (password == null || password.length() < MIN_LENGTH)
            missing.add("ao menos " + MIN_LENGTH + " caracteres");
        if (password != null && password.length() > MAX_LENGTH)
            missing.add("no máximo " + MAX_LENGTH + " caracteres");
        if (password == null || !UPPERCASE.matcher(password).find())
            missing.add("uma letra maiúscula");
        if (password == null || !SPECIAL.matcher(password).find())
            missing.add("um caractere especial");
        return missing;
    }

    /** Diz o que falta, não a regra inteira: quem errou só a maiúscula não precisa reler o resto. */
    public static String describe(List<String> violations) {
        if (violations.size() == 1)
            return "precisa de " + violations.getFirst();
        String last = violations.getLast();
        return "precisa de " + String.join(", ", violations.subList(0, violations.size() - 1))
                + " e " + last;
    }

    /**
     * Para onde bean validation não alcança. Hoje é o aceite de convite, cujo campo de senha só é
     * senha nova em um dos dois caminhos — exigir a política no outro trancaria fora quem tem conta
     * antiga com senha mais curta.
     */
    public static void validateNewPassword(String password) {
        List<String> missing = violations(password);
        if (!missing.isEmpty())
            throw new ValidationException("password: " + describe(missing));
    }
}
