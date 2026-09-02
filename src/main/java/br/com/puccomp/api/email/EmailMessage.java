package br.com.puccomp.api.email;

import java.time.Duration;

/**
 * Os emails que o sistema sabe mandar. Selada porque a renderização casa exaustivamente sobre os
 * tipos: acrescentar um email aqui quebra a compilação até existirem assunto e template para ele.
 */
public sealed interface EmailMessage {

    String to();

    /** Convite para entrar numa EJ; o link já vem montado porque a validade dele é de identity. */
    record Invitation(String to, String organizationName, String acceptUrl, Duration validFor)
            implements EmailMessage { }

    /** Comprovante de inscrição em processo seletivo, para o candidato anônimo. */
    record CandidacyReceived(String to, String candidateName, String processTitle)
            implements EmailMessage { }
}
