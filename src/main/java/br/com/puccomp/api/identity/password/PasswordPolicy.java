package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.shared.exception.ValidationException;

/**
 * Definição única do que é uma senha aceitável. Existe para que o aceite de convite, a redefinição
 * e a troca não divirjam: senha recusada num caminho e aceita no outro deixa a pessoa presa —
 * conseguiria criar no convite uma senha que a redefinição depois se recusa a restaurar.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /** BCrypt trunca em 72 bytes: sem teto, o resto da senha seria ignorado em silêncio. */
    public static final int MAX_LENGTH = 72;

    public static final String MESSAGE = "deve ter entre 8 e 72 caracteres";

    private PasswordPolicy() { }

    /**
     * Para onde bean validation não alcança. No aceite de convite o mesmo campo ora define uma
     * senha, ora prova a posse de uma conta que já existe — e só o primeiro caso é política; exigir
     * o mínimo no segundo trancaria fora quem tem senha antiga mais curta.
     */
    public static void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH)
            throw new ValidationException("password: " + MESSAGE);
    }
}
