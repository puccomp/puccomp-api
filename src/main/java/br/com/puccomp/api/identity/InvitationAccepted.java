package br.com.puccomp.api.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Um convite virou membro. {@code inviterAccountId} é nulo no convite de provisionamento da
 * plataforma — não há quem avisar, e isso é um caso normal.
 */
public record InvitationAccepted(
        UUID tenantId,
        UUID invitationId,
        UUID inviterAccountId,
        String inviteeName,
        String inviteeEmail,
        Instant at
) { }
