package br.com.puccomp.api.shared.exception;

/** Arquivo recusado na validação. Nomeia o caso para os consumidores; o 400 vem de ValidationException. */
public class InvalidFileException extends ValidationException {
    public InvalidFileException(String message) { super(message); }
}
