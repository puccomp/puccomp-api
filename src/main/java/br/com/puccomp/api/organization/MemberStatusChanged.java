package br.com.puccomp.api.organization;

import java.time.Instant;
import java.util.UUID;

/**
 * O vínculo de um membro mudou de estado — o único fato que revoga ou devolve acesso à EJ. Criação
 * não vira transição: quem acabou de aceitar um convite não teve acesso algum alterado.
 */
public record MemberStatusChanged(
        UUID tenantId,
        UUID memberId,
        UUID accountId,
        String memberName,
        Transition transition,
        Instant at
) {

    public enum Transition {

        /** Virou alumnus: mantém a leitura do que viveu, perde a escrita. */
        RETIRED,

        /** Voltou ao quadro ativo. */
        REACTIVATED,

        /** Saiu da EJ: perde todo o acesso e some do contrato. */
        REMOVED,

        /** Voltou depois de ter saído. */
        RESTORED
    }
}
