package br.com.puccomp.api.recruitment;

import java.time.Instant;
import java.util.UUID;

/**
 * Um processo seletivo mudou de fase. {@link Phase} espelha só os estados que interessam a quem
 * avisa: expor o enum interno obrigaria a abrir o pacote de processos para os outros módulos.
 */
public record SelectionProcessPhaseChanged(
        UUID tenantId,
        UUID processId,
        String processTitle,
        Phase phase,
        Instant resultAt,
        Instant at
) {

    public enum Phase {

        /** Inscrições abertas. */
        OPENED,

        /** Inscrições encerradas, avaliação em andamento. */
        IN_REVIEW,

        /** Resultado publicado. */
        CLOSED,

        CANCELLED
    }
}
