package br.com.puccomp.api.organization.members.history;

import br.com.puccomp.api.organization.members.Member;
import br.com.puccomp.api.organization.members.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * O único caminho por onde o estado de um membro muda.
 *
 * <p>Centralizado porque um {@code setStatus} solto em qualquer serviço produziria uma mudança sem
 * evento, e o buraco só apareceria meses depois, num turnover que não fecha. A projeção
 * ({@code Member.status}) e o evento são gravados na mesma transação: ou os dois, ou nenhum.
 */
@Service
@RequiredArgsConstructor
public class MemberLifecycle {

    private final MemberStatusEventRepository events;
    private final OrganizationTrackingRepository tracking;
    private final Clock clock;

    /** Criação de membro — inclusive pelo aceite de convite e pelo seeder. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreation(Member member) {
        append(member.getId(), MemberStatusEventKind.CREATED, null, member.getStatus());
    }

    /**
     * Transição para o mesmo estado é no-op: repetir "aposentar" não pode duplicar a saída nem
     * mover a data dela. Trocar {@code ALUMNUS} por {@code INACTIVE} também não é uma segunda saída
     * — quem já não estava ativo não sai de novo —, e isso cai naturalmente do registro por estado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void changeStatus(Member member, MemberStatus target) {
        MemberStatus current = member.getStatus();
        if (current == target) return;
        member.changeStatus(target);
        append(member.getId(), MemberStatusEventKind.STATUS_CHANGED, current, target);
    }

    /** Marco de cobertura da EJ. Vale inclusive para EJ que ainda não tem membro nenhum. */
    @Transactional(propagation = Propagation.MANDATORY)
    void startTracking(Instant at) {
        if (tracking.findFirstBy().isPresent()) return;
        tracking.save(OrganizationTracking.builder().trackedSince(at).build());
    }

    private void append(UUID memberId, MemberStatusEventKind kind, MemberStatus from, MemberStatus to) {
        events.save(MemberStatusEvent.builder()
                .memberId(memberId)
                .sequence(events.lastSequenceOf(memberId) + 1)
                .kind(kind)
                .fromStatus(from)
                .toStatus(to)
                .occurredAt(clock.instant())
                .build());
    }
}
