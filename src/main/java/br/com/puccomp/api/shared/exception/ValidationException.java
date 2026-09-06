package br.com.puccomp.api.shared.exception;

/**
 * Entrada recusada por regra de domínio, respondida como 400 com a mensagem intacta.
 * Existe para que {@link IllegalArgumentException} acidental (do JDK ou de biblioteca) não
 * vire 400 nem tenha a mensagem interna ecoada para quem chamou.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
