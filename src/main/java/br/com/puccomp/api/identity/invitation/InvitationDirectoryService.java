package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.InvitationDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Convites pendentes de todas as EJs, para a rotina de lembrete. Tenant vem na linha, não no filtro. */
@Service
@RequiredArgsConstructor
class InvitationDirectoryService implements InvitationDirectory {

    private final JdbcClient jdbc;

    @Override
    public List<PendingInvitation> expiringBetween(Instant from, Instant to) {
        return jdbc.sql("""
                select tenant_id, id, email, created_by_account_id as inviter_account_id, expires_at
                from invitations
                where accepted_at is null and revoked_at is null
                  and expires_at >= :from and expires_at < :to
                  and created_by_account_id is not null
                order by expires_at
                """)
                .param("from", from)
                .param("to", to)
                .query(PendingInvitation.class)
                .list();
    }
}
