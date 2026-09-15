package br.com.puccomp.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Convites pendentes para a rotina que lembra quem convidou. Cruza tenants: é manutenção. */
public interface InvitationDirectory {

    List<PendingInvitation> expiringBetween(Instant from, Instant to);

    record PendingInvitation(UUID tenantId, UUID id, String email, UUID inviterAccountId,
                             Instant expiresAt) { }
}
